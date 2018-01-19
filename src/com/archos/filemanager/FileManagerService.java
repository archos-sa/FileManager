package com.archos.filemanager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.archos.filecorelibrary.CopyCutEngine;
import com.archos.filecorelibrary.DeleteEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.OperationEngineListener;
import com.archos.filecorelibrary.zip.ZipCompressionEngine;
import com.archos.filecorelibrary.zip.ZipExtractionEngine;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import android.widget.Toast;


public class FileManagerService extends Service implements OperationEngineListener {

    public static FileManagerService fileManagerService = null;

    private static final int PASTE_NOTIFICATION_ID = 1;
    private static final int OPEN_NOTIFICATION_ID = 2;
    private static String OPEN_AT_THE_END_KEY = "open_at_the_end_key";

    private ArrayList<MetaFile2> mProcessedFiles = null;
    private IBinder localBinder;

    private NotificationManager mNotificationManager;
    private Builder mNotificationBuilder;
    private long mPasteTotalSize = 0;
    private int mCurrentFile = 0;
    private BroadcastReceiver mReceiver;
    private long mLastUpdate = 0;
    private long mLastStatusBarUpdate = 0;
    private boolean mOpenAtTheEnd;
    private boolean mHasOpenAtTheEndBeenSet;
    private FileActionEnum mActionMode;
    private ActionStatusEnum mLastStatus;
    private HashMap<MetaFile2, Long> mProgress;
    private HashMap<MetaFile2, Long> mRootFilesProgress;
    private long mPasteTotalProgress;
    private double mCurrentSpeed = -1.0;
    private CopyCutEngine mCopyCutEngine;
    private ArrayList<ServiceListener> mListeners;
    private boolean mIsActionRunning;
    private DeleteEngine mDeleteEngine;
    private Uri mTarget;
    private ZipCompressionEngine mCompressEngine;
    private ZipExtractionEngine mZipExtractionEngine;
    private int mCurrentRootFile;
    private ArrayList<MetaFile2> mRootFiles;

    public enum FileActionEnum {
        NONE, COPY, CUT, DELETE, COMPRESSION, EXTRACTION
    };

    public enum ActionStatusEnum {
        PROGRESS, START, STOP, CANCELED, ERROR, NONE
    };

    public interface  ServiceListener {
        void onActionStart();
        void onActionStop();
        void onActionError();
        void onActionCanceled();
        void onProgressUpdate();
    }


    public FileManagerService() {
        super();

        fileManagerService = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mLastStatus = ActionStatusEnum.NONE;
        mRootFiles = new ArrayList<>();
        localBinder = new FileManagerServiceBinder();
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mOpenAtTheEnd = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(OPEN_AT_THE_END_KEY, true);
        mHasOpenAtTheEndBeenSet = false;
        mListeners = new ArrayList<>();
        mProcessedFiles = new ArrayList<>();
        mRootFilesProgress = new HashMap<>();
        mProgress = new HashMap<>();
        mCopyCutEngine = new CopyCutEngine(this);
        mCopyCutEngine.setListener(this);
        mDeleteEngine = new DeleteEngine(this);
        mDeleteEngine.setListener(this);
        mIsActionRunning = false;

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() != null) {
                    if (intent.getAction().equals("CANCEL")) {
                        stopPasting();
                    } else if (intent.getAction().equals("OPEN")) {
                        openLastFile();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("CANCEL");
        filter.addAction("OPEN");
        registerReceiver(mReceiver, filter);
    }

    private void openLastFile() {
        if (mTarget != null && mProcessedFiles != null && mProcessedFiles.size() == 1 && mProcessedFiles.get(0).isFile()) {
            mNotificationManager.cancel(OPEN_NOTIFICATION_ID);
            Uri uri = Uri.withAppendedPath(mTarget, mProcessedFiles.get(0).getName());

            String extension = "*";
            if (uri.getLastPathSegment().contains(".") && !uri.getLastPathSegment().endsWith(".")) {
                extension = uri.getLastPathSegment().substring(uri.getLastPathSegment().lastIndexOf(".") + 1);
            }

            String mimeType = mProcessedFiles.get(0).getMimeType();
            if (mimeType == null || mimeType.isEmpty()) {
                mimeType = "*/" + extension;
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (uri != null) {
                intent.setDataAndType(uri, mimeType);
            } else {
                Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
            }
        }

    }

    /**
     * progress hashmap wasn't in the right order when iterating, this one represents the real copy order
     * list of the files being operated by copy/delete/cut
     * @return
     */
    public List<MetaFile2> getFilesToPaste() {
        return mProcessedFiles;
    }

    /**
     * progress hashmap wasn't in the right order when iterating, this one represents the real copy order
     * list of the files being operated by copy/delete/cut
     * @return
     */
    public List<MetaFile2> getRootFilesToPaste() {
        return mRootFiles;
    }

    /**
     * return how many files are currently being operated
     * @return
     */
    public int getPasteTotalFiles() {
        return mProcessedFiles.size();
    }

    /**
     * give the index of current file in list getFilesToPaste
     * @return
     */
    public int getCurrentFile() {
        return mCurrentFile;
    }
    /**
     * give the index of current file in list getFilesToPaste
     * @return
     */
    public int getCurrentRootFile() {
        return mCurrentRootFile;
    }

    /**
     * give the total size (useful when copying multiple files). Size in B
     * @return
     */
    public long getPasteTotalSize() {
        return mPasteTotalSize;
    }

    /**
     * give the total progress (useful when copying multiple files). Size in B
     * @return
     */
    public long getPasteTotalProgress() {
        return mPasteTotalProgress;
    }

    /**
     * give the average copy speed of current file in bytes/second.
     * @return
     */
    public double getCurrentSpeed() {
        return mCurrentSpeed;
    }

    /**
     * Get progress by file. Size in B
     * @return
     */
    public HashMap<MetaFile2, Long> getFilesProgress() {
        return mProgress;
    }

    /**
     * Get progress by file. Size in B
     * @return
     */
    public HashMap<MetaFile2, Long> getRootFilesProgress() {
        return mRootFilesProgress;
    }

    /**
     * get last status of engines (error, stop, etc..)
     * @return mLastStatus
     */
    public ActionStatusEnum getLastStatus() {
        return mLastStatus;
    }

    public Uri getSource() {
        return mProcessedFiles.get(0).getUri();
    }

    public Uri getTarget() {
        return mTarget;
    }

    public void setOpenAtTheEnd(boolean openAtTheEnd) {
        mOpenAtTheEnd = openAtTheEnd;
        PreferenceManager.getDefaultSharedPreferences(this)
        .edit()
        .putBoolean(OPEN_AT_THE_END_KEY, mOpenAtTheEnd)
        .commit();
        mHasOpenAtTheEndBeenSet = true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mReceiver);
    }

    public boolean getOpenAtTheEnd() {
        return mOpenAtTheEnd;
    }

    public boolean hasOpenAtTheEndBeenSet() {
        return mHasOpenAtTheEndBeenSet;
    }

    /**
     * set all variables to initial values
     * @param filesToProcess
     */
    private void initVariables(List<MetaFile2> filesToProcess) {
        mProcessedFiles = new ArrayList<>();
        mProgress.clear();
        mPasteTotalSize = (long) 0;
        mCurrentFile = 0;
        mPasteTotalProgress = 0;
        mCurrentSpeed = -1.0;
        mHasOpenAtTheEndBeenSet = false;
        mRootFiles.clear();
        mRootFilesProgress.clear();
        mProcessedFiles.addAll(filesToProcess);
        mRootFiles.addAll(filesToProcess);
        for (MetaFile2 mf : mProcessedFiles) {
            mProgress.put(mf,(long) 0);
            mRootFilesProgress.put(mf,(long) 0);
            mPasteTotalSize += mf.length();
        }
    }

    public boolean cut(List<MetaFile2> FilesToPaste, Uri target) {
        return copyCut(FilesToPaste, target, FileActionEnum.CUT);
    }

    public void copy(List<MetaFile2> FilesToPaste, Uri target) {
        copyCut(FilesToPaste, target, FileActionEnum.COPY);
    }

    private boolean copyCut(List<MetaFile2> FilesToPaste, Uri target, FileActionEnum actionMode) {
        if (!mIsActionRunning) {
            mIsActionRunning = true;
            initVariables(FilesToPaste);
            mActionMode = actionMode;
            mTarget = target;
            ArrayList<MetaFile2> sources = new ArrayList<MetaFile2>(mProcessedFiles.size());
            sources.addAll(mProcessedFiles);
            if (actionMode == FileActionEnum.COPY) {
                mCopyCutEngine.copy(sources, target, false);
            } else {
                mCopyCutEngine.cut(sources, target, false);
            }
            startStatusbarNotification();
            return true;
        }

        return false;
    }

    public void extract(List<MetaFile2> filesExtract, Uri target) {
        if (!mIsActionRunning) {
            mIsActionRunning = true;
            initVariables(filesExtract);
            mActionMode = FileActionEnum.EXTRACTION;
            mTarget = target;
            if (mZipExtractionEngine == null) {
                mZipExtractionEngine = new ZipExtractionEngine(this, this);
            }
            ArrayList<MetaFile2> sources = new ArrayList<MetaFile2>(mProcessedFiles.size());
            sources.addAll(mProcessedFiles);
            mZipExtractionEngine.extract(sources, target);
            startStatusbarNotification();
        }
    }

    public void compress(List<MetaFile2> filesToCompress, Uri target) {
        if (!mIsActionRunning) {
            mIsActionRunning = true;
            initVariables(filesToCompress);
            mActionMode = FileActionEnum.COMPRESSION;
            mTarget = target;
            if (mCompressEngine == null) {
                mCompressEngine = new ZipCompressionEngine(this);
            }
            ArrayList<MetaFile2> sources = new ArrayList<MetaFile2>(mProcessedFiles.size());
            sources.addAll(mProcessedFiles);
            mCompressEngine.compress(sources, target);
            startStatusbarNotification();
        }
    }

    /*
     * is currently doing something
     */
    public boolean isActionRunning() {
        return mIsActionRunning;
    }

    public void addListener(ServiceListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    private PendingIntent getCancelIntent() {
        Intent intent = new Intent("CANCEL");
        return PendingIntent.getBroadcast(this, 0, intent, 0);
    }

    private Intent getOpenIntent() {
        Intent intent = new Intent("OPEN");
        return intent;
    }

    /*
     * was last action a copy action
     */
    public boolean isCopyAction() {
        return mActionMode == FileActionEnum.COPY;
    }

    public boolean isCutAction() {
        return mActionMode == FileActionEnum.CUT;
    }

    public boolean isDeleteAction() {
        return mActionMode == FileActionEnum.DELETE;
    }

    public boolean isCompressionAction() {
        return mActionMode == FileActionEnum.COMPRESSION;
    }

    public boolean isExtractionAction() {
        return mActionMode == FileActionEnum.EXTRACTION;
    }

    private void updateStatusbarNotification(String text) {
        if (text != null) {
            // Update the notification text
            mNotificationBuilder.setContentText(text);
        }
        // Tell the notification manager about the changes
        mNotificationManager.notify(PASTE_NOTIFICATION_ID, mNotificationBuilder.build());
    }

    protected void removeStatusbarNotification() {
        if (mNotificationBuilder != null) {
            mNotificationManager.cancel(PASTE_NOTIFICATION_ID);
            mNotificationBuilder = null;
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return localBinder;
    }

    public class FileManagerServiceBinder extends Binder {
        public FileManagerService getService() {
            return FileManagerService.this;
        }
    }

    public void stopPasting() {
        if (mIsActionRunning) {
            switch (getActionMode()) {
                case COMPRESSION:
                    if (mCompressEngine != null) {
                        mCompressEngine.stop();
                    }
                    break;
                case EXTRACTION:
                    if (mZipExtractionEngine != null) {
                        mZipExtractionEngine.stop();
                    }
                    break;
                case DELETE:
                    if (mDeleteEngine != null) {
                        mDeleteEngine.stop();
                    }
                    break;
                default:
                    if (mCopyCutEngine != null) {
                        mCopyCutEngine.stop();
                    }
                    break;
            }
        }
    }

    public void deleteObserver(ServiceListener listener) {
        mListeners.remove(listener);
    }

    public void delete(List<MetaFile2> filestodelete) {
        if (!mIsActionRunning) {
            mIsActionRunning = true;
            initVariables(filestodelete);
            mActionMode = FileActionEnum.DELETE;
            ArrayList<MetaFile2> sources = new ArrayList<MetaFile2>(mProcessedFiles.size());
            sources.addAll(mProcessedFiles);
            mDeleteEngine.delete(filestodelete);
        }
    }

    @Override
    public void onStart() {
        mLastStatus = ActionStatusEnum.START;
        mIsActionRunning = true;
        startStatusbarNotification();

        long totalSize = 0;
        for (MetaFile2 mf : mProcessedFiles) {
            totalSize += mf.length();
        }
        updateStatusbarNotification(0, totalSize, 0, mProcessedFiles.size());

        for (ServiceListener fl : mListeners) {
            fl.onActionStart();
        }
    }

    @Override
    public void onEnd() {
        mLastStatus = ActionStatusEnum.STOP;
        mIsActionRunning = false;
        removeStatusbarNotification();

        if (mActionMode == FileActionEnum.COPY) {
            if (mHasOpenAtTheEndBeenSet) {
                if (mOpenAtTheEnd) {
                    openLastFile();
                } else {
                    displayOpenFileNotification();
                }
            }
        }

        for (ServiceListener fl : mListeners) {
            fl.onActionStop();
        }
    }

    @Override
    public void onFatalError(Exception e) {
        mLastStatus = ActionStatusEnum.ERROR;
        mIsActionRunning = false;
        removeStatusbarNotification();

        for (ServiceListener fl : mListeners) {
            fl.onActionError();
        }
    }

    public FileActionEnum getActionMode() {
        return mActionMode;
    }

    @Override
    public void onProgress(int currentFile, long currentFileProgress, int currentRootFile, long rootFileProgress, long totalProgress, double currentSpeed) {
        mLastStatus = ActionStatusEnum.PROGRESS;
        if (currentFile < mProcessedFiles.size()) {
            if (currentFileProgress == -1) {
                mProgress.put(mProcessedFiles.get(currentFile), mProcessedFiles.get(currentFile).length());
            } else {
                mProgress.put(mProcessedFiles.get(currentFile), currentFileProgress);
            }
        }
        mCurrentFile = currentFile;
        mCurrentRootFile = currentRootFile;
        if (mCurrentRootFile < mRootFiles.size()) {
            if (rootFileProgress == -1) {
                mRootFilesProgress.put(mRootFiles.get(currentRootFile), mRootFiles.get(currentRootFile).length());
            } else {
                mRootFilesProgress.put(mRootFiles.get(currentRootFile), rootFileProgress);
            }
        }
        mPasteTotalProgress = totalProgress;
        mCurrentSpeed = currentSpeed;
        if (System.currentTimeMillis() - mLastUpdate > 200) {
            mLastUpdate = System.currentTimeMillis();
            notifyListeners();
        }
        if (System.currentTimeMillis() - mLastStatusBarUpdate > 1000) { // updating too often would prevent user from touching the cancel button
            mLastStatusBarUpdate = System.currentTimeMillis();
            updateStatusbarNotification(totalProgress, getPasteTotalSize(), currentFile + 1, mProcessedFiles.size());
        }
    }

    private void notifyListeners() {
        for (ServiceListener lis : mListeners){
            lis.onProgressUpdate();
        }
    }

    @Override
    public void onFilesListUpdate(List<MetaFile2> copyingMetaFiles, List<MetaFile2> rootFiles) {
        mProcessedFiles.clear();
        mProcessedFiles.addAll(copyingMetaFiles);
        mProgress.clear();
        mRootFilesProgress.clear();
        mRootFiles.clear();
        mRootFiles.addAll(rootFiles);
        mPasteTotalSize = (long) 0;
        mPasteTotalProgress = 0;
        mCurrentSpeed = -1.0;
        for (MetaFile2 mf : mProcessedFiles) {
            mProgress.put(mf, (long) 0);
            mPasteTotalSize += mf.length();
        }
        for (MetaFile2 mf : rootFiles) {
            mRootFilesProgress.put(mf, (long) 0);
        }

        updateStatusbarNotification(0, mPasteTotalSize, 0, mProcessedFiles.size());
    }

    private Builder prepareNotificationBuilder() {
        Builder notificationBuilder = new NotificationCompat.Builder(this);
        notificationBuilder.setSmallIcon(R.drawable.notification_filemanager);
        notificationBuilder.setTicker(null);
        notificationBuilder.setOnlyAlertOnce(true);
        notificationBuilder.setWhen(System.currentTimeMillis());
        notificationBuilder.setDefaults(0); // no sound, no light, no vibrate
        return notificationBuilder;
    }

    /* Notification */

    public void startStatusbarNotification() {
        mNotificationManager.cancel(OPEN_NOTIFICATION_ID);
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // Set the title and icon
        int message;
        switch (mActionMode) {
            case COMPRESSION:
                message = R.string.zip_compressing_message;
                break;
            case EXTRACTION:
                message = R.string.zip_extracting_message;
                break;
            case DELETE:
                message = R.string.deleting;
                break;
            case CUT:
                message = R.string.moving;
                break;
            default:
                message =  R.string.copying;
                break;
        }
        CharSequence title = getResources().getText(message);

        // Build the intent to send when the user clicks on the notification in the notification panel
        Intent notificationIntent = new Intent(this, RootActivity.class);
        notificationIntent.putExtra("LAUNCH_DIALOG", true);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        // Create a new notification builder
        mNotificationBuilder = prepareNotificationBuilder();
        if (mIsActionRunning) {
            mNotificationBuilder.addAction(R.drawable.small_cross, getString(android.R.string.cancel), getCancelIntent());
        }
        mNotificationBuilder.setOngoing(true);
        mNotificationBuilder.setContentTitle(title);
        mNotificationBuilder.setContentIntent(contentIntent);

        // Set the info to display in the notification panel and attach the notification to the notification manager
        updateStatusbarNotification(null);
    }

    private void displayOpenFileNotification() {
        Builder notificationBuilder = prepareNotificationBuilder();
        Intent notificationIntent = getOpenIntent();
        CharSequence title = getResources().getText(R.string.open_file);
        PendingIntent contentIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, 0);
        notificationBuilder.setContentTitle(title);
        notificationBuilder.setContentText(mProcessedFiles.get(0).getName());
        notificationBuilder.setContentIntent(contentIntent);
        mNotificationManager.notify(OPEN_NOTIFICATION_ID, notificationBuilder.build());
    }

    private void updateStatusbarNotification(long currentSize, long totalSize, int currentFile, int totalFiles) {

        if (mNotificationBuilder != null) {
            String formattedCurrentSize = Formatter.formatShortFileSize(this, currentSize);
            String formattedTotalSize = Formatter.formatShortFileSize(this, totalSize);
            int textId;
            String formattedString;

            if (totalFiles <= 0) {
                switch (mActionMode) {
                    case COMPRESSION:
                        textId = R.string.zip_compressing_one;
                        break;
                    case EXTRACTION:
                        textId = R.string.zip_extracting_one;
                        break;
                    case DELETE:
                        textId = R.string.deleting_one;
                        break;
                    case CUT:
                        textId = R.string.pasting_cut_one;
                        break;
                    default:
                        textId =  R.string.pasting_copy_one;
                        break;
                }

                // Display the progress in bytes only
                if (mActionMode != FileActionEnum.DELETE && mActionMode != FileActionEnum.COMPRESSION) {
                    formattedString = getResources().getString(textId, formattedCurrentSize, formattedTotalSize);
                } else {
                    formattedString = getResources().getString(textId);
                }
            }
            else {
                switch (mActionMode) {
                    case COMPRESSION:
                        textId = R.string.zip_compressing_many;
                        break;
                    case EXTRACTION:
                        textId = R.string.zip_extracting_many;
                        break;
                    case DELETE:
                        textId = R.string.deleting_many;
                        break;
                    case CUT:
                        textId = R.string.pasting_cut_many;
                        break;
                    default:
                        textId =  R.string.pasting_copy_many;
                        break;
                }

                // Display the progress in number of files and bytes
                if (mActionMode != FileActionEnum.DELETE && mActionMode != FileActionEnum.COMPRESSION) {
                    formattedString = getResources().getString(textId, currentFile, totalFiles, formattedCurrentSize, formattedTotalSize);
                } else {
                    formattedString = getResources().getString(textId, currentFile, totalFiles);
                }
            }

            if (currentSize != -1) {
                mNotificationBuilder.setProgress((int)totalSize, (int)currentSize, false);
            } else {
                mNotificationBuilder.setProgress(totalFiles, currentFile, false);
            }
            updateStatusbarNotification(formattedString);
        }
    }

    private void setCanceledStatus() {
        mLastStatus = ActionStatusEnum.CANCELED;
        mIsActionRunning = false;
        removeStatusbarNotification();
        for (ServiceListener lis : mListeners) {
            lis.onActionCanceled();
        }
        mNotificationManager.cancel(OPEN_NOTIFICATION_ID);
    }

    @Override
    public void onCanceled() {
        setCanceledStatus();
    }

    @Override
    public void onSuccess(Uri target) {}

}
