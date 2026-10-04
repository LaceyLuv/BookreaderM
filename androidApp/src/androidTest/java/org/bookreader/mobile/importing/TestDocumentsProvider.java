package org.bookreader.mobile.importing;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A separate-UID fixture using only framework/JDK APIs. Standalone test-APK processes cannot
 * load the Kotlin runtime that AGP packages exclusively in the target APK.
 */
public final class TestDocumentsProvider extends DocumentsProvider {
    private static final String[] ROOT_COLUMNS = {
        DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.COLUMN_MIME_TYPES
    };
    private static final String[] DOCUMENT_COLUMNS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_FLAGS
    };

    @Override public boolean onCreate() { return true; }

    @Override public Cursor queryRoots(String[] projection) {
        String[] columns = projection == null ? ROOT_COLUMNS : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] values = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            switch (columns[index]) {
                case DocumentsContract.Root.COLUMN_ROOT_ID:
                case DocumentsContract.Root.COLUMN_DOCUMENT_ID: values[index] = "fixtures"; break;
                case DocumentsContract.Root.COLUMN_TITLE: values[index] = "BookReader test TXT"; break;
                case DocumentsContract.Root.COLUMN_FLAGS:
                    values[index] = DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD; break;
                case DocumentsContract.Root.COLUMN_MIME_TYPES:
                    values[index] = "text/plain\napplication/octet-stream"; break;
                default: values[index] = null;
            }
        }
        cursor.addRow(values);
        return cursor;
    }

    @Override public boolean isChildDocument(String parentDocumentId, String documentId) {
        return "fixtures".equals(parentDocumentId) && !"fixtures".equals(documentId);
    }

    @Override public Cursor queryDocument(String documentId, String[] projection) {
        return documents(projection, new String[] { documentId });
    }

    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        return documents(projection, new String[] { "system-picker", "utf8", "unknown", "legacy", "binary" });
    }

    private Cursor documents(String[] projection, String[] ids) {
        String[] columns = projection == null ? DOCUMENT_COLUMNS : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        for (String id : ids) {
            Object[] values = new Object[columns.length];
            for (int index = 0; index < columns.length; index++) {
                switch (columns[index]) {
                    case DocumentsContract.Document.COLUMN_DOCUMENT_ID: values[index] = id; break;
                    case DocumentsContract.Document.COLUMN_DISPLAY_NAME:
                        values[index] = "system-picker".equals(id) ? "System picker fixture.txt" : "../../" + id + ".txt";
                        break;
                    case DocumentsContract.Document.COLUMN_MIME_TYPE:
                        values[index] = "fixtures".equals(id) ? DocumentsContract.Document.MIME_TYPE_DIR : "text/plain";
                        break;
                    case DocumentsContract.Document.COLUMN_SIZE:
                        if ("unknown".equals(id) || "stalled".equals(id)) values[index] = null;
                        else if ("oversize".equals(id)) values[index] = 256L * 1024 * 1024 + 1;
                        else values[index] = (long) bytes(id).length;
                        break;
                    case DocumentsContract.Document.COLUMN_FLAGS: values[index] = 0; break;
                    default: values[index] = null;
                }
            }
            cursor.addRow(values);
        }
        return cursor;
    }

    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
        throws java.io.FileNotFoundException {
        if (!"r".equals(mode)) throw new IllegalArgumentException("Read-only fixture");
        if ("stalled".equals(documentId)) {
            try {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                Thread writer = new Thread(() -> {
                    try (ParcelFileDescriptor.AutoCloseOutputStream output =
                        new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                        output.write("부분 본문\n".getBytes(StandardCharsets.UTF_8));
                        Thread.sleep(30_000);
                    } catch (IOException expectedWhenReaderCancels) {
                        // Closing the owned read end is the cancellation behavior under test.
                    } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }, "bookreader-fixture-stalled");
                writer.start();
                return pipe[0];
            } catch (IOException failure) {
                throw new java.io.FileNotFoundException("Unable to create fixture pipe");
            }
        }
        File file = new File(getContext().getCacheDir(), "fixture-" + documentId);
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes(documentId)); }
        catch (IOException failure) { throw new java.io.FileNotFoundException("Unable to create TXT fixture"); }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private byte[] bytes(String id) {
        switch (id) {
            case "reader-scroll": case "reader-host": case "reader-failure":
                StringBuilder text = new StringBuilder();
                for (int line = 0; line < 6000; line++) {
                    if (line != 0) text.append('\n');
                    String number = Integer.toString(line);
                    text.append(id).append(" 본문 ");
                    for (int zero = number.length(); zero < 5; zero++) text.append('0');
                    text.append(number).append(" · 읽기 위치와 줄바꿈을 검증하는 자체 생성 문장입니다.");
                }
                return text.toString().getBytes(StandardCharsets.UTF_8);
            case "system-picker": return "시스템 파일 선택기로 추가한 실제 TXT입니다.\n".getBytes(StandardCharsets.UTF_8);
            case "empty": return new byte[0];
            case "legacy": return new byte[] { (byte) 0xb0, (byte) 0xa1, 0x0a };
            case "binary": return new byte[] { 0x50, 0x4b, 0x03, 0x04, 0, 1 };
            case "unknown": return "크기를 알 수 없는 문서입니다.\n".getBytes(StandardCharsets.UTF_8);
            default: return "실제 문서 제공자에서 가져온 TXT입니다.\n원본을 보존합니다.\n".getBytes(StandardCharsets.UTF_8);
        }
    }
}
