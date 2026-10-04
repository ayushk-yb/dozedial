package com.dozedial.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.dozedial.data.db.HeartRateRecordEntity
import com.dozedial.data.db.SleepSessionEntity
import java.io.File
import java.io.FileWriter
import java.io.StringWriter
import java.io.Writer
import java.time.Instant

/**
 * Utility for serializing recorded sleep session heart rate telemetry into standard CSV format
 * and launching the Android system share sheet.
 */
object SessionDataExporter {

    const val CSV_HEADER = "session_id,iso_timestamp,timestamp_epoch_ms,elapsed_seconds,bpm,sensor_contact,raw_hex"

    /**
     * Serializes session heart rate records to a [Writer].
     */
    fun writeCsv(
        writer: Writer,
        session: SleepSessionEntity,
        records: List<HeartRateRecordEntity>
    ) {
        val baseTimeMs = session.startTime
        writer.append(CSV_HEADER).append("\n")
        for (record in records) {
            val isoTimestamp = Instant.ofEpochMilli(record.timestampMs).toString()
            val elapsedSeconds = ((record.timestampMs - baseTimeMs).coerceAtLeast(0L)) / 1000L
            val contactStr = record.sensorContact?.toString() ?: ""
            // Format: session_id,iso_timestamp,timestamp_epoch_ms,elapsed_seconds,bpm,sensor_contact,raw_hex
            writer.append(session.sessionId.toString())
                .append(',')
                .append(isoTimestamp)
                .append(',')
                .append(record.timestampMs.toString())
                .append(',')
                .append(elapsedSeconds.toString())
                .append(',')
                .append(record.bpm.toString())
                .append(',')
                .append(contactStr)
                .append(',')
                .append(record.rawBytesHex)
                .append('\n')
        }
        writer.flush()
    }

    /**
     * Converts session records into a CSV String (useful for unit testing).
     */
    fun exportToCsvString(
        session: SleepSessionEntity,
        records: List<HeartRateRecordEntity>
    ): String {
        val stringWriter = StringWriter()
        writeCsv(stringWriter, session, records)
        return stringWriter.toString()
    }

    /**
     * Exports a session to a CSV file in the app cache directory under exports/.
     */
    fun exportSessionToFile(
        context: Context,
        session: SleepSessionEntity,
        records: List<HeartRateRecordEntity>
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "dozedial_session_${session.sessionId}_${session.startTime}.csv"
        val file = File(exportDir, fileName)

        FileWriter(file).use { writer ->
            writeCsv(writer, session, records)
        }
        return file
    }

    /**
     * Creates an [Intent.ACTION_SEND] Intent sharing the exported CSV file via [FileProvider].
     */
    fun createShareIntent(context: Context, file: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "DozeDial Sleep Recording — ${file.name}")
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
