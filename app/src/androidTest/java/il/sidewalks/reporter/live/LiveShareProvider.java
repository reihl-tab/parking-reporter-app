package il.sidewalks.reporter.live;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Serves the CI-staged owner photo as content:// so the live submission E2E
 * exercises the real share-entry path (file:// is refused by EntryIntents).
 * Written in plain Java ON PURPOSE: the provider runs in the test package's
 * own process, whose classloader has no kotlin-stdlib (see TestShareProvider).
 * The staged file exists only while the manual live workflow runs; with no
 * staged file every open is refused. Test builds only, never production.
 */
public class LiveShareProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File staged = new File(getContext().getFilesDir(), "live-photo.jpg");
        if (!staged.isFile()) {
            throw new FileNotFoundException("no staged live photo");
        }
        return ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY);
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
