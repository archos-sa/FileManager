package com.archos.filemanager;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import android.content.Context;
import android.os.Environment;

import com.archos.environment.ArchosUtils;
import com.archos.environment.SystemPropertiesProxy;
import com.archos.filecorelibrary.MetaFile;
import com.archos.filecorelibrary.MetaFile2;

public class FileManagerUtils {
	protected static final String SAMBA_PATH = "smb://";
    protected static final String STORAGE_PATH = Environment.getExternalStorageDirectory().getPath();

    private final static HashMap<String, Integer> mimeTypeIcons = new HashMap<String, Integer>(32);
    static {
        mimeTypeIcons.put("application/vnd.android.package-archive", R.drawable.filetype_apk);
        mimeTypeIcons.put("application/aos-update", R.drawable.filetype_aos);
        mimeTypeIcons.put("application/fw-update", R.drawable.filetype_img);

        mimeTypeIcons.put("application/x-rar-compressed",R.drawable.filetype_rar);
        mimeTypeIcons.put("application/vnd.oasis.opendocument.text", R.drawable.filetype_word);
        mimeTypeIcons.put("application/msword", R.drawable.filetype_word);
        mimeTypeIcons.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", R.drawable.filetype_word);
        mimeTypeIcons.put("application/xhtml+xml", R.drawable.filetype_html);
        mimeTypeIcons.put("text/html", R.drawable.filetype_html);
        mimeTypeIcons.put("application/x-bittorrent", R.drawable.filetype_torrent);
        mimeTypeIcons.put("audio", R.drawable.filetype_music);
        mimeTypeIcons.put("application/epub+zip",R.drawable.filetype_epub);
        mimeTypeIcons.put("application/ogg", R.drawable.filetype_music);
        mimeTypeIcons.put("application/x-flac", R.drawable.filetype_music);
        mimeTypeIcons.put("application/x-ogg", R.drawable.filetype_music);
        mimeTypeIcons.put("application/pdf", R.drawable.filetype_pdf);
        mimeTypeIcons.put("image", R.drawable.filetype_picture);

        mimeTypeIcons.put("application/x-gtar", R.drawable.filetype_gzip);
        mimeTypeIcons.put("audio/mpegurl", R.drawable.filetype_playlist);
        mimeTypeIcons.put("audio/x-mpegurl", R.drawable.filetype_playlist);
        mimeTypeIcons.put("application/vnd.ms-powerpoint", R.drawable.filetype_powerpoint);
        mimeTypeIcons.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", R.drawable.filetype_powerpoint);
        mimeTypeIcons.put("video", R.drawable.filetype_video);
        mimeTypeIcons.put("video/x-flv", R.drawable.filetype_flash);
        mimeTypeIcons.put("application/vnd.oasis.opendocument.spreadsheet", R.drawable.filetype_excel);
        mimeTypeIcons.put("application/vnd.ms-excel", R.drawable.filetype_excel);
        mimeTypeIcons.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", R.drawable.filetype_excel);
        mimeTypeIcons.put("application/octet-stream", R.drawable.filetype_subtitles);
        mimeTypeIcons.put("application/smil", R.drawable.filetype_subtitles);
        mimeTypeIcons.put("application/zip", R.drawable.filetype_zip);
    }


	

	




    /*
    public static HashMap<String, Integer> getMimeTypeDrawables() {
        return mimeTypeIcons;
    }*/

    // keep in sync with com.archos.mediaprovider.ArchosMediaFile and com.archos.video.utils.VideoUtils
    private static final String[] SUBTITLES = {"idx", "smi", "ssa", "ass", "srr", "srt", "sub", "mpl"/*, "txt"*/};

    static public int getIconResIdForFile(MetaFile2 file) {
        if (file.isDirectory()) {
            return R.drawable.filetype_folder;
        }
        else {
            return getMimeTypeIconResId(file.getMimeType(), file.getExtension());
        }
    }

    static public int getMimeTypeIconResId(String mimeType, String extension) {
        int resId = R.drawable.filetype_generic;
        if (mimeType == null) {
            return resId;
        }

        for (Entry<String, Integer> entry : mimeTypeIcons.entrySet()) {
            if (mimeType.startsWith(entry.getKey()))
                resId = entry.getValue();
        }

        // Subtitle icon.
        if (mimeType.isEmpty() || "text/plain".equals(mimeType)) {
            boolean oneMore = true;
            for(int i = 0, length = SUBTITLES.length; oneMore && i < length; i++) {
                if (SUBTITLES[i].equals(extension)) {
                    oneMore = false;
                    resId = mimeTypeIcons.get("application/smil");
                }
            }
        }

        return resId;
    }

    static public String getMultiFilesStringOneLine(List<MetaFile2> list) {
        return getMultiFilesString(list, ", ");
    }



    /*
     * Append the names of the files
     */
    static public String getMultiFilesString(List<MetaFile2> list, String separator) {
        if (list.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (MetaFile2 f : list) {
            if (!first) {
                sb.append(separator);
            }
            sb.append(f.getName());
            first = false;
        }

        return sb.toString();
    }   
}
