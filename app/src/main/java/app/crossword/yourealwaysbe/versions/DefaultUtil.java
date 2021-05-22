package app.crossword.yourealwaysbe.versions;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.Window;

import app.crossword.yourealwaysbe.forkyz.ForkyzApplication;
import app.crossword.yourealwaysbe.io.IO;
import app.crossword.yourealwaysbe.puz.PuzzleMeta;
import app.crossword.yourealwaysbe.util.files.DirHandle;
import app.crossword.yourealwaysbe.util.files.FileHandle;
import app.crossword.yourealwaysbe.util.files.FileHandler;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map.Entry;
import java.util.Map;

public abstract class DefaultUtil implements AndroidVersionUtils {
    public abstract void setContext(Context ctx);

    public boolean downloadFile(URL url, FileHandle destination,
            Map<String, String> headers, boolean notification, String title) {

        FileHandler fileHandler
            = ForkyzApplication.getInstance().getFileHandler();

        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestProperty("Connection", "close");

            for (Entry<String, String> e : headers.entrySet()){
                conn.setRequestProperty(e.getKey(), e.getValue());
            }

            try (
                OutputStream fos = fileHandler.getOutputStream(destination);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                InputStream is = new BufferedInputStream(conn.getInputStream());
            ) {
                IO.copyStream(is, baos);
                IO.copyStream(
                    new ByteArrayInputStream(baos.toByteArray()),
                    fos
                );
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public abstract void onActionBarWithText(MenuItem a);

    public abstract void onActionBarWithText(SubMenu reveal);

    public void hideWindowTitle(AppCompatActivity a) {
        a.requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    public abstract void onActionBarWithoutText(MenuItem a);


    public abstract boolean checkBackgroundDownload(SharedPreferences prefs, boolean hasWritePermissions);

    public abstract void clearBackgroundDownload(SharedPreferences prefs);

}
