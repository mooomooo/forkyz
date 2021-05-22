
package app.crossword.yourealwaysbe.net;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Map;
import java.util.logging.Level;

import android.net.Uri;

import app.crossword.yourealwaysbe.forkyz.ForkyzApplication;
import app.crossword.yourealwaysbe.io.JPZIO;
import app.crossword.yourealwaysbe.util.files.DirHandle;
import app.crossword.yourealwaysbe.util.files.FileHandle;
import app.crossword.yourealwaysbe.util.files.FileHandler;

/**
 * Abstract for puzzle sources using .JPZ XML format
 */
public abstract class AbstractJPZDownloader extends AbstractDownloader {

    public AbstractJPZDownloader(
        String baseUrl,
        DirHandle downloadDirectory,
        String downloaderName
    ) {
        super(baseUrl, downloadDirectory, downloaderName);
    }

    @Override
    public Downloader.DownloadResult download(LocalDate date) {
        return download(date, this.createUrlSuffix(date), EMPTY_MAP);
    }

    @Override
    protected Downloader.DownloadResult download(
        LocalDate date,
        String urlSuffix,
        Map<String, String> headers
    ) {
        URL url = null;
        try {
            url = new URL(this.baseUrl + urlSuffix);
        } catch (MalformedURLException e) {
            LOG.log(
                Level.SEVERE,
                "Error downloading " + getName() + " puzzle: " + e
            );
            return null;
        }

        FileHandler fileHandler
            = ForkyzApplication.getInstance().getFileHandler();

        String fileName = this.createFileName(date);

        FileHandle f = fileHandler.createFileHandle(
            this.downloadDirectory,
            this.createFileName(date),
            FileHandler.MIME_TYPE_PUZ
        );
        if (f == null)
            return null;

        boolean success = false;

        try (
            InputStream is = new BufferedInputStream(url.openStream());
            DataOutputStream dos = new DataOutputStream(
                 fileHandler.getBufferedOutputStream(f)
            )
        ) {
            success =
                JPZIO.convertPuzzle(is, dos, date);

            if (!success) {
                LOG.log(
                    Level.SEVERE,
                    "Unable to convert " + getName()
                        + " XML puzzle into Across Lite format."
                );
            } else {
                return new Downloader.DownloadResult(f);
            }
        } catch (IOException ioe) {
            LOG.log(
                Level.SEVERE,
                "Exception converting " + getName()
                    + " XML puzzle into Across Lite format.",
                ioe
            );
        } finally {
            if (!success)
                fileHandler.delete(f);
        }

        return null;
    }
}

