package com.dealright.smsforwarder

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlin.random.Random

/**
 * Delivers one SMS to its webhook.
 *
 * JobScheduler owns the run: it holds a wakelock, keeps the process from being
 * frozen, and (as an expedited job on Android 12+) gives it network access in
 * Doze even when the app is battery-optimized. The job is persisted, so it
 * survives the process being killed and a reboot.
 */
class ForwardJob : JobService() {

    private var thread: Thread? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val e = params.extras
        thread = Thread {
            val keys = e.getStringArray(KEY_HEADER_NAMES) ?: emptyArray()
            val values = e.getStringArray(KEY_HEADER_VALUES) ?: emptyArray()
            val headerConfig = HeaderConfig(
                httpMethod = e.getString(KEY_METHOD) ?: "POST",
                contentType = e.getString(KEY_CONTENT_TYPE) ?: "application/json",
                extraHeaders = keys.zip(values).toMap(),
            )
            val willRetry = System.currentTimeMillis() - e.getLong(KEY_QUEUED_AT) < GIVE_UP_AFTER_MS
            // Retry quick failures (DNS not up yet after Doze, a network handover) inside
            // this run: a rescheduled job waits out its backoff, longer in a low standby bucket.
            for (attempt in 0..IN_RUN_DELAYS_SECONDS.size) {
                val last = attempt == IN_RUN_DELAYS_SECONDS.size
                val done = WebhookForwarder.send(
                    sender = e.getString(KEY_SENDER) ?: "",
                    smsBody = e.getString(KEY_SMS_BODY) ?: "",
                    webhookUrl = e.getString(KEY_URL) ?: "",
                    rendered = e.getString(KEY_RENDERED) ?: "",
                    headerConfig = headerConfig,
                    willRetry = willRetry,
                    logFailure = last,
                )
                if (done) return@Thread jobFinished(params, false)
                if (!last) {
                    try {
                        Thread.sleep(IN_RUN_DELAYS_SECONDS[attempt] * 1000)
                    } catch (_: InterruptedException) {
                        return@Thread // onStopJob already asked for a reschedule
                    }
                }
            }
            jobFinished(params, willRetry)
        }.also { it.start() }
        return true
    }

    // The system stopped the job (network lost, quota spent): run it again later.
    override fun onStopJob(params: JobParameters): Boolean {
        thread?.interrupt()
        return true
    }

    companion object {
        private val IN_RUN_DELAYS_SECONDS = listOf(5L, 10L, 20L, 40L, 60L)
        private const val GIVE_UP_AFTER_MS = 24 * 60 * 60 * 1000L

        private const val KEY_SENDER = "sender"
        private const val KEY_SMS_BODY = "sms_body"
        private const val KEY_URL = "url"
        private const val KEY_RENDERED = "rendered"
        private const val KEY_METHOD = "method"
        private const val KEY_CONTENT_TYPE = "content_type"
        private const val KEY_HEADER_NAMES = "header_names"
        private const val KEY_HEADER_VALUES = "header_values"
        private const val KEY_QUEUED_AT = "queued_at"

        fun enqueue(
            context: Context,
            smsData: SmsData,
            webhookUrl: String,
            rendered: String,
            headerConfig: HeaderConfig,
        ) {
            val extras = PersistableBundle().apply {
                putString(KEY_SENDER, smsData.sender)
                putString(KEY_SMS_BODY, smsData.body)
                putString(KEY_URL, webhookUrl)
                putString(KEY_RENDERED, rendered)
                putString(KEY_METHOD, headerConfig.httpMethod)
                putString(KEY_CONTENT_TYPE, headerConfig.contentType)
                putStringArray(KEY_HEADER_NAMES, headerConfig.extraHeaders.keys.toTypedArray())
                putStringArray(KEY_HEADER_VALUES, headerConfig.extraHeaders.values.toTypedArray())
                putLong(KEY_QUEUED_AT, System.currentTimeMillis())
            }
            val builder = JobInfo.Builder(Random.nextInt(), ComponentName(context, ForwardJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .setExtras(extras)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setExpedited(true)
            }
            val scheduler = context.getSystemService(JobScheduler::class.java)
            // An app out of expedited quota is refused; queue it as a regular job instead.
            if (scheduler.schedule(builder.build()) != JobScheduler.RESULT_SUCCESS &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                scheduler.schedule(builder.setExpedited(false).build())
            }
        }
    }
}
