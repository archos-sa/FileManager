
package com.archos.filemanager;

import com.archos.environment.SystemPropertiesProxy;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawListerFactory;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.archos.filecorelibrary.localstorage.JavaFile2;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.text.format.Formatter;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class InfoDialog extends Dialog {

    final private static String TAG = "InfoDialog";
    final private static boolean DBG = true;

    final private static String EMULATED_STORAGE_ROOT = "/storage/sdcard0";
    final private static String SD_STORAGE_ROOT = "/sd/external";
    final private static String INTERNAL_STORAGE_ROOT = "/data/media";

    private class SizeInfoData {
        public SizeInfoData() {}
        public SizeInfoData(SizeInfoData toCopy) {
            this.size = toCopy.size;
            this.sizeSecondary = toCopy.sizeSecondary;
            this.numberOfFiles = toCopy.numberOfFiles;
            this.numberOfDirectories = toCopy.numberOfDirectories;
            this.onGoingComputing = toCopy.onGoingComputing;
        }

        /**
         * File size in bytes
         */
        long size;
        /**
         * Another file size, used to get the size on SD storage, in case of Unified storage
         */
        long sizeSecondary;
        /**
         * used only in case of multiple selection
         */
        int numberOfFiles;
        /**
         * used only in case of multiple selection
         */
        int numberOfDirectories;
        /**
         * True if the computing is on-going, meaning the values are temporary only
         */
        boolean onGoingComputing;
    }

    Context mC;

    // Values that can be stored before onCreate is called
    private boolean mUnifiedStorageActivated;

    private TextView mTitleView;
    private ImageView mIconView;

    private View mPrimarySizeGroup;
    private View mSecondarySizeGroup;
    private TextView mSizeLabelTv;
    private TextView mSizeTv;
    private TextView mSizeLabelSDTv;
    private TextView mSizeSDTv;
    private ProgressBar mSizeProgressPb;
    private TextView mNumberFilesTv;
    private TextView mPermissionTv;
    private TextView mPermissionLabelTv;
    private TextView mLastModifiedTv;
    private TextView mLastModifiedLabelTv;
    private TextView mMimeTypeTv;
    private TextView mMimeTypeLabelTv;
    private TextView mFullPathTv;

    private List<MetaFile2> mFiles;
    private GetInfoThread mGetInfoThread;

    public InfoDialog(Context context) {
        super(context, R.style.InfoDialog);
        mC = context;
        mUnifiedStorageActivated = SystemPropertiesProxy.getBoolean("persist.sys.archos.unioned", false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.info_dialog);
        setCancelable(true);
        setCanceledOnTouchOutside(true);

        View root = findViewById(R.id.dialog_root_layout);

        mTitleView = (TextView) root.findViewById(R.id.archos_info_title);
        mIconView = (ImageView) root.findViewById(R.id.archos_info_icon);

        mPrimarySizeGroup = root.findViewById(R.id.primary_size_group);
        mSecondarySizeGroup = root.findViewById(R.id.secondary_size_group);
        mSizeTv = (TextView) mPrimarySizeGroup.findViewById(R.id.archos_info_size);
        mSizeLabelTv = (TextView) mPrimarySizeGroup.findViewById(R.id.archos_info_size_label);
        mSizeSDTv = (TextView) mSecondarySizeGroup.findViewById(R.id.archos_info_size_2);
        mSizeLabelSDTv = (TextView) mSecondarySizeGroup.findViewById(R.id.archos_info_size_label_2);

        mSizeProgressPb = (ProgressBar)root.findViewById(R.id.archos_info_progress);
        mNumberFilesTv = (TextView)root.findViewById(R.id.archos_info_number_files);

        mPermissionTv = (TextView)root.findViewById(R.id.archos_info_permission);
        mPermissionLabelTv = (TextView)root.findViewById(R.id.archos_info_permission_label);
        mLastModifiedTv = (TextView)root.findViewById(R.id.archos_info_last_modified);
        mLastModifiedLabelTv = (TextView)root.findViewById(R.id.archos_info_last_modified_label);
        mMimeTypeTv = (TextView)root.findViewById(R.id.archos_info_mime_type);
        mMimeTypeLabelTv = (TextView)root.findViewById(R.id.archos_info_mime_type_label);
        mFullPathTv = (TextView)root.findViewById(R.id.archos_info_fullpath);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        cancel();
        return true;
    }

    protected void start() {
        // Start the thread if the file list has already been set up
        if (mFiles != null) {
            initUI();
            startAsyncFileInfo();
        }
    }

    protected void stop() {
        if (mGetInfoThread != null) {
            mGetInfoThread.stopThread();
            mGetInfoThread = null;
        }
    }

    protected void setFiles(List<MetaFile2> files) {
        mFiles = files;

        // Start the thread if the dialog is already showing
        if (isShowing()) {
            initUI();
            startAsyncFileInfo();
        }
    }

    /**
     * Initialize UI, depending on the files to display
     * Done before starting the thread
     */
    private void initUI() {

        // Depending on the Life-Cycle use-case, the files may not be set yet
        if (mFiles != null) {
            if (mFiles.size()==1) {
                MetaFile2 file = mFiles.get(0);
                mTitleView.setText(file.getName());
                mIconView.setImageResource(FileManagerUtils.getIconResIdForFile(file));

                int read = file.canRead() ? R.string.file_info_label_can_read : R.string.file_info_label_cannot_read;
                int write = file.canWrite() ? R.string.file_info_label_can_write : R.string.file_info_label_cannot_write;
                mPermissionTv.setText(mC.getText(read) + ", " + mC.getText(write));
                mPermissionLabelTv.setVisibility(View.VISIBLE);
                mPermissionTv.setVisibility(View.VISIBLE);

                mLastModifiedTv.setText(getFormatedDate(file.lastModified()));
                mLastModifiedLabelTv.setVisibility(View.VISIBLE);
                mLastModifiedTv.setVisibility(View.VISIBLE);

                mMimeTypeTv.setText(file.getMimeType());
                mMimeTypeLabelTv.setVisibility(View.VISIBLE);
                mMimeTypeTv.setVisibility(View.VISIBLE);

                mFullPathTv.setText(file.getUri().getPath());
                mFullPathTv.setVisibility(View.VISIBLE);
            }
            else {
                mTitleView.setText(FileManagerUtils.getMultiFilesStringOneLine(mFiles));
                mIconView.setImageResource(R.drawable.filetype_multiple_files);

                // Lots of fields are invalid for multiple files
                mPermissionLabelTv.setVisibility(View.GONE);
                mPermissionTv.setVisibility(View.GONE);
                mLastModifiedLabelTv.setVisibility(View.GONE);
                mLastModifiedTv.setVisibility(View.GONE);
                mMimeTypeLabelTv.setVisibility(View.GONE);
                mMimeTypeTv.setVisibility(View.GONE);
                mFullPathTv.setVisibility(View.GONE);
            }
        }

        if (mUnifiedStorageActivated && hasOnlyLocalFiles(mFiles)) {
            // change labels of file size
            mSizeLabelTv.setText(R.string.file_info_label_size_internal);
            mSizeLabelSDTv.setText(R.string.file_info_label_size_sd);
            mSecondarySizeGroup.setVisibility(View.VISIBLE);
        }
        else {
            mSizeLabelTv.setText(R.string.file_info_label_size);
            mSecondarySizeGroup.setVisibility(View.GONE);
        }
    }

    private void startAsyncFileInfo() {
        // abort previous thread if for some reason there is one
        if (mGetInfoThread!=null) {
            mGetInfoThread.stopThread();
        }
        mGetInfoThread = new GetInfoThread(mFiles, mHandler);
        mGetInfoThread.start();
    }

    private void updateSizeInfo(SizeInfoData data) {
        // Hide the progress when the processing is over
        if (data.onGoingComputing) {
            mSizeProgressPb.setVisibility(View.VISIBLE);
            final int grey = mC.getResources().getColor(R.color.info_dialog_text_color_grey);
            mSizeTv.setTextColor(grey);
            mSizeSDTv.setTextColor(grey);
            mNumberFilesTv.setTextColor(grey);
        }
        else {
            mSizeProgressPb.setVisibility( View.INVISIBLE);
            final int regular = mC.getResources().getColor(R.color.info_dialog_text_color);
            mSizeTv.setTextColor(regular);
            mSizeSDTv.setTextColor(regular);
            mNumberFilesTv.setTextColor(regular);
        }

        // In case there is only one file that is not a folder, no need to keep the count details line
        if (mFiles.size()==1 && mFiles.get(0).isFile()) {
            mSizeProgressPb.setVisibility(View.GONE);
            mNumberFilesTv.setVisibility(View.GONE);
        }

        mSizeTv.setText(Formatter.formatFileSize(mC, data.size));
        mSizeTv.setVisibility(View.VISIBLE);
        mSizeLabelTv.setVisibility(View.VISIBLE);

        if (data.sizeSecondary>0) {
            mSizeSDTv.setText(Formatter.formatFileSize(mC, data.sizeSecondary));
            mSizeLabelSDTv.setVisibility(View.VISIBLE);
            mSizeSDTv.setVisibility(View.VISIBLE);
            // Hide internal size in case it is null (and if there is a secondary size)
            if (data.size==0) {
                mSizeTv.setVisibility(View.GONE);
                mSizeLabelTv.setVisibility(View.GONE);
            }
        } else {
            mSizeLabelSDTv.setVisibility(View.GONE);
            mSizeSDTv.setVisibility(View.GONE);
        }

        if ((data.numberOfDirectories + data.numberOfFiles) > 0) {
            if (data.numberOfFiles==0) {
                mNumberFilesTv.setText(R.string.file_info_directory_empty);
            } else {
                mNumberFilesTv.setText(formatDirectoryInfo(mC, data.numberOfDirectories, data.numberOfFiles));
            }
        }
    }

    // Handler to get some asynchronous info
    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == GetInfoThread.MSG_SIZE_INFO_DATA) {
                SizeInfoData data = (SizeInfoData)msg.obj;
                updateSizeInfo(data);
            }
        }
    };

    private String getFormatedDate(long time){
        java.text.DateFormat dateFormat = android.text.format.DateFormat.getLongDateFormat(getContext());
        String s = dateFormat.format(time);
        java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getContext());
        s +=" "+ timeFormat.format(time);
        return s;
    }

    // Nested Thread to get file(s) info asynchronously in a Thread (mandatory in case of network access)
    private final class GetInfoThread extends Thread {
        private boolean mStopThread = false;
        private final Collection<MetaFile2> mFiles;
        private final Handler mListener;
        private long mTimeWhenLastSizeUpdateWasSent = 0;

        public static final int MSG_SIZE_INFO_DATA = 2016;

        public GetInfoThread(Collection<MetaFile2> files, Handler listener) {
            mFiles = files;
            mListener = listener;
        }

        public void stopThread() {
            mStopThread = true;
        }

        public void run() {
            SizeInfoData sizeInfo = new SizeInfoData();
            sizeInfo.onGoingComputing = true;
            addFiles(mFiles, sizeInfo); // This size computing works for files as well as folders!

            if (!mStopThread) {
                sizeInfo.onGoingComputing = false;
                mListener.obtainMessage(MSG_SIZE_INFO_DATA, new SizeInfoData(sizeInfo)).sendToTarget();
            }
        }

        public void addFiles(Collection<MetaFile2> files, SizeInfoData info) {
            // Process recursively each item of the selection
            for (MetaFile2 f : files) {
                if (mStopThread) {
                    if(DBG) Log.d(TAG, "mStopThread=true");
                    return;
                }
                if (f.isDirectory()) {
                    addDirectory(f, info);
                    sendUpdateTolistenerIfNeeded(info);
                }
                else {
                    addFile(f, info);
                    sendUpdateTolistenerIfNeeded(info);
                }
            }
        }

        /**
         * Add a folder to the total file size computing
         */
        private void addDirectory(MetaFile2 directory, SizeInfoData info) {
            info.numberOfDirectories++;

            // Get the contents of this folder
            Collection<MetaFile2> files = Collections.emptyList();

            try {
                files = RawListerFactory.getRawListerForUrl(directory.getUri()).getFileList();
            }
            catch (SecurityException e) {
                // May occur if the folder is read-only
            } catch (AuthenticationException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SftpException e) {
                e.printStackTrace();
            } catch (JSchException e) {
                e.printStackTrace();
            }

            addFiles(files, info);
        }

        /**
         * Add a file to the total file size computing
         */
        private void addFile(MetaFile2 f, SizeInfoData info) {
            // Special case for local files when there is unified storage
            if (mUnifiedStorageActivated && (f instanceof JavaFile2)) {
                info.numberOfFiles++;
                // size of the internal file
                String internalPath = f.getUri().getPath().replace(EMULATED_STORAGE_ROOT, INTERNAL_STORAGE_ROOT);
                File internalFile = new File(internalPath);
                if (internalFile.exists()) {
                    info.size+=internalFile.length();
                }
                else {
                    // size in case of external file
                    String sdPath = f.getUri().getPath().replace(EMULATED_STORAGE_ROOT, SD_STORAGE_ROOT);
                    File sdFile = new File(sdPath);
                    if (sdFile.exists()) {
                        info.sizeSecondary+=sdFile.length();
                    }
                }
            }
            else {
                // easier when there is no unified storage!
                info.numberOfFiles++;
                info.size += f.length();
            }
        }

        private void sendUpdateTolistenerIfNeeded(SizeInfoData info) {
            final long now = SystemClock.elapsedRealtime();
            if (now - mTimeWhenLastSizeUpdateWasSent > 100) { // update at ~10fps
                mTimeWhenLastSizeUpdateWasSent = now;
                if (!mStopThread) {
                    // safer to send a copy of the info instance
                    mListener.obtainMessage(MSG_SIZE_INFO_DATA, new SizeInfoData(info)).sendToTarget();
                }
            }
        }
    }

    static public String formatDirectoryInfo(Context context, int directories, int files) {
        String res = null;

        if (directories == 1) {
            res = context.getText(R.string.file_info_one_directory).toString();
        } else if (directories > 1) {
            res = directories + " " + context.getText(R.string.file_info_directories).toString();
        }

        if (res == null) {
            res = "";
        } else if (directories != 0 && files != 0) {
            res += ", ";
        }

        if (files == 1) {
            res += context.getText(R.string.file_info_one_file).toString();
        } else if (files > 1) {
            res += files + " " + context.getText(R.string.file_info_files).toString();
        }

        return res;
    }

    static private boolean hasOnlyLocalFiles(List<MetaFile2> files) {
        for (MetaFile2 f : files) {
            if (f.isRemote()) {
                return false;
            }
        }
        return true;
    }
}
