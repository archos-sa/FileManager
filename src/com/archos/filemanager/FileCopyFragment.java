package com.archos.filemanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filemanager.FileManagerService.ServiceListener;

import java.util.HashMap;
import java.util.List;


public class FileCopyFragment extends Fragment implements ServiceListener {

    private LinearLayoutManager mLayoutManager;
    private RecyclerView mRecycleView;
    private FileCopyAdapter mAdapter;
    private HashMap<MetaFile2, Long> mData;
    private TextView mProgressText;
    private View mMinimizeButton;
    private View mCancelButton;
    private View mDoneButton;
    private int mCurrentPosition;
    private List<MetaFile2> mOrderedPastingFiles;
    private TextView mAction;
    private ProgressBar mProgressBar;
    private ProgressBar mProgressSmall;
    private CheckBox mOpenAtTheEnd;

    /**
     * when we touch the recyclerview, stop auto scrolling
     */
    private static final int ENABLE_SCROLL = 0;
    private boolean mCanIScroll = true;

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == ENABLE_SCROLL) {
                mCanIScroll = true;
            }
        }
    };

    private Listener mListener;

    public interface Listener {
        public void onMinimizePressed();
        public void onCancelPressed();
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); 

        mCurrentPosition = 0;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup viewGroup, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.file_copy_fragment, viewGroup, false);

        mData = new HashMap<MetaFile2, Long>();
        mAdapter = new FileCopyAdapter(mData, getActivity());
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecycleView = (RecyclerView)v.findViewById(R.id.my_recycler_view);
        mRecycleView.setLayoutManager(mLayoutManager);
        mRecycleView.setAdapter(mAdapter);
        mRecycleView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mHandler.removeMessages(ENABLE_SCROLL);
                mCanIScroll = false;
                mHandler.sendEmptyMessageDelayed(ENABLE_SCROLL, 2000);
                return false;
            }
        });

        mAction = (TextView)v.findViewById(R.id.action);
        mProgressText = (TextView)v.findViewById(R.id.progress_text);
        mProgressBar = (ProgressBar)v.findViewById(R.id.progress);
        mProgressSmall = (ProgressBar)v.findViewById(R.id.progress_small);

        mOpenAtTheEnd = (CheckBox)v.findViewById(R.id.open_file);
        mOpenAtTheEnd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (FileManagerService.fileManagerService != null) {
                    FileManagerService.fileManagerService.setOpenAtTheEnd(isChecked);
                }
            }
        });

        mMinimizeButton = v.findViewById(R.id.minimize);
        mMinimizeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onMinimizePressed();
                }
            }
        });

        mDoneButton = v.findViewById(R.id.done);
        mDoneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mListener != null) {
                    mListener.onMinimizePressed();
                }
            }
        });

        mCancelButton = v.findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (FileManagerService.fileManagerService != null) {
                    FileManagerService.fileManagerService.stopPasting();
                }
                if (mListener != null) {
                    mListener.onCancelPressed();
                }
            }
        });

        return v;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (FileManagerService.fileManagerService != null) {
            FileManagerService.fileManagerService.addListener(this);
        }
        init();
    }

    private void init() {
        if (FileManagerService.fileManagerService != null) {
            switch (FileManagerService.fileManagerService.getLastStatus()) {
                case NONE:
                    break;
                case START:
                case PROGRESS:
                    onProgressUpdate();
                    break;
                case CANCELED:
                    onActionCanceled();
                    break;
                case STOP:
                    onActionStop();
                    break;
                case ERROR:
                    onActionError();
                    break;
            }
        }
    }

    @Override
    public void onDetach() {
        if (FileManagerService.fileManagerService != null) {
            FileManagerService.fileManagerService.deleteObserver(this);
        }

        super.onDetach();
    }

    /**
     * callback for minimize and cancel buttons
     * @param listener
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void onProgressUpdate() {
        if (!isAdded()) // too late
            return;
        setButtonsState(true);

        if (FileManagerService.fileManagerService != null && FileManagerService.fileManagerService.getFilesProgress() != null) {

            long totalSize = FileManagerService.fileManagerService.getPasteTotalSize();
            long totalProgress = FileManagerService.fileManagerService.getPasteTotalProgress();
            final double currentSpeed = FileManagerService.fileManagerService.getCurrentSpeed();
            final double currentSpeedKbs = (currentSpeed != -1.0) ? (currentSpeed / 1024.0) : -1.0;
            final int currentPosition = FileManagerService.fileManagerService.getCurrentRootFile();
            final int currentFile = FileManagerService.fileManagerService.getCurrentFile();
            int totalFilesToPaste = FileManagerService.fileManagerService.getPasteTotalFiles();
            boolean isDeleting = FileManagerService.fileManagerService.isDeleteAction();

            mData = FileManagerService.fileManagerService.getRootFilesProgress();
            mOrderedPastingFiles = FileManagerService.fileManagerService.getRootFilesToPaste(); // hashmap wasn't in the right order when iterating, this one represents the real copy order

            if (FileManagerService.fileManagerService.hasOpenAtTheEndBeenSet()) { // if copy initiated by an open will (for example user has clicked on an smb file)
                mOpenAtTheEnd.setVisibility(View.VISIBLE);
                if (FileManagerService.fileManagerService.getOpenAtTheEnd() != mOpenAtTheEnd.isChecked()) {
                    mOpenAtTheEnd.setChecked(FileManagerService.fileManagerService.getOpenAtTheEnd());
                }
            } else {
                mOpenAtTheEnd.setVisibility(View.GONE);
            }

            // Scroll
            if (mRecycleView != null && mLayoutManager.getItemCount() > currentPosition && !mRecycleView.isDirty() && currentPosition != mCurrentPosition) // avoid checking layoutmanager too many times
            {
                if (mData.size() > currentPosition) {
                    mRecycleView.post(new Runnable() {
                        @Override
                        public void run() {
                            if (!(mLayoutManager.findFirstCompletelyVisibleItemPosition() <= currentPosition && mLayoutManager.findLastCompletelyVisibleItemPosition() >= currentPosition))
                            {
                                if (mCanIScroll) {
                                    mRecycleView.scrollToPosition(currentPosition);
                                    mCurrentPosition = currentPosition;
                                }
                            }
                        }
                    });
                }
            }

            // progress update
            if (mAdapter != null) {
                mAdapter.setProgress(mData);   
                mAdapter.setOrderedFiles(mOrderedPastingFiles);
                mAdapter.notifyDataSetChanged();
            }
            if (mProgressText != null && mAction != null) {
                mProgressBar.setMax(!isDeleting ? (int)totalSize : totalFilesToPaste);
                mProgressBar.setProgress(!isDeleting ? (int)totalProgress : currentPosition);
                String message;
                String title;
                switch (FileManagerService.fileManagerService.getActionMode()) {
                    case COMPRESSION:
                        message = getResources().getString(R.string.zip_compressing_many, currentFile + 1, totalFilesToPaste);
                        title = getResources().getString(R.string.zip_compressing_message);
                        break;
                    case EXTRACTION:
                        title = getResources().getString(R.string.zip_extracting_message);
                        message = getResources().getString(R.string.zip_extracting_many, currentFile + 1, totalFilesToPaste);
                        break;
                    case DELETE:
                        message = getResources().getString(R.string.deleting_many, currentFile + 1, totalFilesToPaste);
                        title = getResources().getString(R.string.deleting);
                        break;
                    case CUT:
                        title = getResources().getString(R.string.moving);
                        message = getResources().getString(R.string.pasting_cut_many,
                                currentFile + 1, totalFilesToPaste,
                                Formatter.formatShortFileSize(getActivity(), totalProgress), Formatter.formatShortFileSize(getActivity(), totalSize));
                        if (currentSpeedKbs != -1.0) {
                            message += getResources().getString(R.string.pasting_copy_speed, String.format("%.1f", currentSpeedKbs));
                        }
                        break;
                    default:
                        title = getResources().getString(R.string.copying);
                        message = getResources().getString(R.string.pasting_copy_many,
                                currentFile + 1, totalFilesToPaste,
                                Formatter.formatShortFileSize(getActivity(), totalProgress), Formatter.formatShortFileSize(getActivity(), totalSize));
                        if (currentSpeedKbs != -1.0) {
                            message += getResources().getString(R.string.pasting_copy_speed, String.format("%.1f", currentSpeedKbs));
                        }
                        break;
                }
                mAction.setText(title);
                mProgressText.setText(message);
            }
        }
    }

    private void setButtonsState(boolean isCopyRunning) {
        if (isCopyRunning) {
            mCancelButton.setVisibility(View.VISIBLE);
            mMinimizeButton.setVisibility(View.VISIBLE);
            mDoneButton.setVisibility(View.GONE);
        } else {
            mCancelButton.setVisibility(View.GONE);
            mMinimizeButton.setVisibility(View.GONE);
            mDoneButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onActionStart() {
        if (!isAdded()) //too late
            return;
        setButtonsState(true);
    }

    @Override
    public void onActionStop() {
        if (!isAdded()) //too late
            return;

        if (mAction != null) {
            onProgressUpdate();
            int message;
            switch (FileManagerService.fileManagerService.getActionMode()) {
                case COMPRESSION:
                    message = R.string.zip_compressing_success;
                    break;
                case EXTRACTION:
                    message = R.string.zip_extract_success_message;
                    break;
                case DELETE:
                    message = R.string.delete_done;
                    break;
                case CUT:
                    message = R.string.cut_done;
                    break;
                default:
                    message =  R.string.copy_done;
                    break;
            }
            setButtonsState(false);
            mProgressBar.setMax(1);
            mProgressBar.setProgress(1);
            mAction.setText(message);
            mProgressSmall.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onActionError() {
        if (!isAdded()) //too late
            return;

        if (mAction != null) {
            onProgressUpdate();
            int message;
            switch (FileManagerService.fileManagerService.getActionMode()) {
                case COMPRESSION:
                    message = R.string.zip_compress_error_message;
                    break;
                case DELETE:
                    message = R.string.delete_error;
                    break;
                case EXTRACTION:
                    message = R.string.zip_extract_error_message;
                    break;
                case CUT:
                    message = R.string.cut_error;
                    break;
                default:
                    message =  R.string.copy_error;
                    break;
            }
            setButtonsState(false);
            mAction.setText(message);
            mProgressSmall.setVisibility(View.INVISIBLE);
        }        
    }

    @Override
    public void onActionCanceled() {
        if (!isAdded()) //too late
            return;

        if (mAction != null) {
            onProgressUpdate();
            int message;
            switch (FileManagerService.fileManagerService.getActionMode()) {
                case COMPRESSION:
                    message = R.string.zip_compressing_canceled;
                    break;
                case EXTRACTION:
                    message = R.string.zip_extract_canceled;
                    break;
                case DELETE:
                    message = R.string.delete_canceled;
                    break;
                case CUT:
                    message = R.string.cut_canceled;
                    break;
                default:
                    message =  R.string.copy_canceled;
                    break;
            }
            setButtonsState(false);
            mAction.setText(message);
            mProgressSmall.setVisibility(View.INVISIBLE);
        }   
    }

}
