package il.sidewalks.reporter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/**
 * Serves one generated JPEG as content:// so the share-entry instrumented
 * test exercises the real content:// path (file:// is refused by
 * EntryIntents). Written in plain Java ON PURPOSE: the provider runs in the
 * test package's own process, whose classloader has no kotlin-stdlib (the
 * Kotlin version died with NoClassDefFoundError: Intrinsics, and the app's
 * openInputStream then blocked until the test timed out). The image is
 * generated into this package's cacheDir on first use, never read from the
 * app's storage.
 */
public class TestShareProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    private synchronized File jpeg(boolean metadata) throws Exception {
        File file = new File(getContext().getCacheDir(), metadata ? "testshare-synthetic-metadata-v2.jpg" : "testshare-synthetic.jpg");
        if (!file.isFile()) {
            File tmp = new File(getContext().getCacheDir(), metadata ? "testshare-synthetic-metadata-v2.jpg.tmp" : "testshare-synthetic.jpg.tmp");
            Bitmap bmp = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888);
            bmp.setPixel(1, 1, Color.BLUE);
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } finally {
                out.close();
            }
            // Synthetic-only fixture metadata. Never changes a real/live photo.
            if (metadata) {
            android.media.ExifInterface exif = new android.media.ExifInterface(tmp.getAbsolutePath());
            exif.setAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL, "2026:01:01 12:00:00");
            exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE, "31/1,46/1,12/1");
            exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE_REF, "N");
            exif.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE, "35/1,12/1,4932/100");
            exif.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE_REF, "E");
            exif.saveAttributes();
            }
            if (!tmp.renameTo(file)) {
                throw new IllegalStateException("could not stage synthetic JPEG");
            }
        }
        return file;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        try {
            return ParcelFileDescriptor.open(jpeg("/with-metadata.jpg".equals(uri.getPath())), ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception e) {
            FileNotFoundException failure = new FileNotFoundException(String.valueOf(e.getMessage()));
            failure.initCause(e);
            throw failure;
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
