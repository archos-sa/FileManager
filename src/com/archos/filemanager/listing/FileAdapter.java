package com.archos.filemanager.listing;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.localstorage.JavaFile2;
import com.archos.filemanager.ArchosCheckBox;
import com.archos.filemanager.FileManagerUtils;
import com.archos.filemanager.InfoDialog;
import com.archos.filemanager.R;

import java.util.ArrayList;
import java.util.List;


public class FileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    @SuppressWarnings("unused")
    private static final String TAG = "FileAdapter";

    final private Context mContext;

    private List<? extends MetaFile2> mFiles;
    private ArrayList<MetaFile2> mSelectedFiles;
    private OnFileClickListener mOnFileClickListener;
    private OnFileLongClickListener mOnFileLongClickListener;
    private OnSelectionChangedListener mOnSelectionChangedListener;

    private ArrayList<FileViewHolder> viewHolderList;

    private void toggleItem(final MetaFile2 file, ArchosCheckBox checkbox) {
        int lastSize = mSelectedFiles.size();
        if(mSelectedFiles.contains(file)){
            mSelectedFiles.remove(file);
        }
        else{
            mSelectedFiles.add(file);
        }

        if(lastSize<=0 && mSelectedFiles.size()>0 || mSelectedFiles.size()==0 && lastSize>0){
            mOnSelectionChangedListener.onSelectionChanged(true,false);
            checkbox.setChecked(mSelectedFiles.contains(file));
            changeCheckboxVisibility(true,false);
        }
        else {
            mOnSelectionChangedListener.onSelectionChanged(false,false);
            checkbox.setChecked(mSelectedFiles.contains(file));
        }
    }

    /**
     * Update checkbox visibility with animation
     * if selectAll is true, then every checkbox will be checked
     *
     * if none are true, checkbox won't change
     * @param selectAll
     *     */
    private void changeCheckboxVisibility(boolean animate,boolean selectAll){
        for(final FileViewHolder vh : viewHolderList)
            if (mSelectedFiles.size() > 0) {

                if(animate)
                    vh.getRoot().setTranslationX( - vh.getCheckbox().getResources().getDimensionPixelSize(R.dimen.checkbox_width));
                vh.getCheckbox().setVisibility(View.VISIBLE);
                if(selectAll)
                    vh.getCheckbox().setChecked(true);
                if(animate)
                    vh.getRoot().animate().translationX(0).setDuration(300).start();
                vh.getRoot().post(new Runnable() {
                    /*
                        post will be sent after animation
                        some view, after checkboxes appear, overflow the recyclerview and animation isn't executed.
                        animation won't start so withendaction won't be called
                     */
                    @Override
                    public void run() {
                        vh.getRoot().setTranslationX(0);
                    }
                });



            } else {
                vh.getCheckbox().setVisibility(View.GONE);
                vh.getCheckbox().setChecked(false);
                if(animate) {
                    vh.getRoot().setTranslationX(vh.getCheckbox().getResources().getDimensionPixelSize(R.dimen.checkbox_width));
                    vh.getRoot().animate().translationX(0).setDuration(300).start();
                }
            }
    }

    public interface OnFileClickListener {
        public void onFileClick(MetaFile2 file, View v);
    }

    public interface OnFileLongClickListener {
        public boolean onFileLongClick(MetaFile2 file, View v, int position);
    }

    public interface OnSelectionChangedListener{
        public void onSelectionChanged(boolean changeState, boolean select);
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener){
        mOnSelectionChangedListener = listener;
    }

    public void setOnFileClickListener(OnFileClickListener listener) {
        mOnFileClickListener = listener;
    }

    public void setOnFileLongClickListener(OnFileLongClickListener listener) {
        mOnFileLongClickListener = listener;
    }

    public class FileViewHolder extends RecyclerView.ViewHolder {
        private final View mContainer;
        private final ImageView mIcon;
        private final TextView mMainTv;
        private final TextView mSecondaryTv;
        private final ArchosCheckBox mCheckbox;
        private final LinearLayout mRoot;

        public FileViewHolder(View v) {
            super(v);
            mContainer = v;
            // Define click listener for the ViewHolder's View.
            v.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mOnFileClickListener.onFileClick(mFiles.get(getAdapterPosition()), v);
                }
            });

            // Define long click listener for the ViewHolder's View.
            v.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    final int position = getAdapterPosition();
                    return mOnFileLongClickListener.onFileLongClick(mFiles.get(position), v, position);
                }
            });

            mIcon = (ImageView) v.findViewById(R.id.icon);
            mIcon.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    toggleItem(mFiles.get(getAdapterPosition()), mCheckbox);
                }
            });
            mIcon.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    toggleItem(mFiles.get(getAdapterPosition()), mCheckbox);
                    return true;
                }
            });
            mMainTv = (TextView) v.findViewById(R.id.name);
            mSecondaryTv = (TextView) v.findViewById(R.id.info);
            mCheckbox = (ArchosCheckBox) v.findViewById(R.id.select);
            mRoot = (LinearLayout) v.findViewById(R.id.list_item);
            mCheckbox.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    toggleItem(mFiles.get(getAdapterPosition()), mCheckbox);
                }
            });
        }
        public View getContainer() {
            return mContainer;
        }
        public LinearLayout getRoot() {
            return mRoot;
        }
        public ImageView getIcon() {
            return mIcon;
        }
        public TextView getMainTextView() {
            return mMainTv;
        }
        public TextView getSecondaryTextView() {
            return mSecondaryTv;
        }
        public ArchosCheckBox getCheckbox() {
            return mCheckbox;
        }
    }

    public FileAdapter(Context context) {
        setHasStableIds(false);
        mContext = context;
        mFiles = new ArrayList<MetaFile2>(); // init with empty list for convenience
        mSelectedFiles= new ArrayList<MetaFile2>();
        viewHolderList = new ArrayList<FileViewHolder>();
    }

    public void updateData(List<? extends MetaFile2> files) {
        mFiles=files;
        // remove from selection metafile that are not there anymore
        ArrayList<MetaFile2> toRemove = new ArrayList<MetaFile2>(); // to avoid concurrent modification when iterating
        for(MetaFile2 mf : mSelectedFiles){
            if(!mFiles.contains(mf)){
                toRemove.add(mf);
            }
        }
        mSelectedFiles.removeAll(toRemove);
        mOnSelectionChangedListener.onSelectionChanged(true,false);
        notifyDataSetChanged();
    }

    /**
     * Store the file list (and possibly other things. Like selection?) in a bundle
     * @param outState
     */
    @SuppressWarnings("unchecked")
    public void onSaveInstanceState(Bundle outState) {
        ArrayList<MetaFile2> files;

        // Convert to ArrayList (if needed) because it is serializable
        if (mFiles instanceof ArrayList<?>) {
            files = (ArrayList<MetaFile2>)mFiles;
        } else {
            files = new ArrayList<MetaFile2>(mFiles);
        }
        outState.putSerializable("mFiles", files);
        outState.putSerializable("mSelectedFiles", mSelectedFiles);
    }

    /**
     * Restore the state that has been saved in a bundle
     * @param inState
     */
    @SuppressWarnings("unchecked")
    public void onRestoreInstanceState(Bundle inState) {
        mFiles = (ArrayList<MetaFile2>)inState.getSerializable("mFiles");
        mSelectedFiles = (ArrayList<MetaFile2>) inState.getSerializable("mSelectedFiles");
    }

    // Create new views (invoked by the layout manager)
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View v = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_file, viewGroup, false);
        FileViewHolder viewHolder = new FileViewHolder(v);
        viewHolderList.add(viewHolder);
        v.setTag(viewHolder);
        return viewHolder;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, final int position) {
        final MetaFile2 file = mFiles.get(position); // no separator offset to handle here! see "trick" comment in the source code above for explanation 
        FileViewHolder fileViewHolder = (FileViewHolder)viewHolder;
        fileViewHolder.getIcon().setImageResource(FileManagerUtils.getIconResIdForFile(file));
        fileViewHolder.getMainTextView().setText(file.getName());
        String secondaryLine = buildSecondaryLine(file);
        fileViewHolder.getSecondaryTextView().setText(secondaryLine);
        fileViewHolder.getSecondaryTextView().setVisibility(secondaryLine.length()==0 ? View.GONE : View.VISIBLE);
        fileViewHolder.getCheckbox().setVisibility(mSelectedFiles.size() > 0 ? View.VISIBLE : View.GONE);
        fileViewHolder.getCheckbox().setCheckedNoAnimation(mSelectedFiles.contains(mFiles.get(position)));
    }

    private String buildSecondaryLine(MetaFile2 f) {
        // Special detailed case for local storage
        if (f instanceof JavaFile2) {
            final JavaFile2 localFile = (JavaFile2)f;
            if (localFile.isDirectory()) {
                int numerOfDirectories = localFile.getNumberOfDirectoriesInside();
                int numberOfFiles = localFile.getNumberOfFilesInside();
                if (numerOfDirectories==0 && numberOfFiles==0) {
                    return mContext.getText(R.string.directory_empty).toString();
                }
                else if (numerOfDirectories==JavaFile2.NUMBER_UNKNOWN && numberOfFiles==JavaFile2.NUMBER_UNKNOWN) {
                    return ""; // we don't know
                }
                else {
                    return InfoDialog.formatDirectoryInfo(mContext, numerOfDirectories, numberOfFiles);
                }
            }
            else {
                return Formatter.formatShortFileSize(mContext, localFile.length());
            }
        }

        // Default case for all other kind of files: display size if not null
        if (f.isFile() && f.length()>=0) {
            return Formatter.formatShortFileSize(mContext, f.length());
        }

        return ""; // default is empty string
    }

    @Override
    public int getItemCount() {
        return mFiles.size();
    }

    public void deselectAll() {
        if(mSelectedFiles.size()>0){
            mSelectedFiles.clear();
            if(mOnSelectionChangedListener!=null)
                mOnSelectionChangedListener.onSelectionChanged(false,true);
            changeCheckboxVisibility(true,false);
        }
    }

    public List<? extends MetaFile2> getFiles() {
        return mFiles;
    }

    public MetaFile2 getFile(int position) {
        return (position >= 0 && position < mFiles.size()) ? mFiles.get(position) : null;
    }

    public List<MetaFile2> getSelectedFiles() {
        return mSelectedFiles;
    }

    public void selectAll() {
        int lastSize = mSelectedFiles.size();
        mSelectedFiles.clear();
        mSelectedFiles.addAll(mFiles);
        mOnSelectionChangedListener.onSelectionChanged((lastSize == 0), true);
        changeCheckboxVisibility((lastSize == 0), true);
    }

    public void selectFile(final MetaFile2 file, View v) {
        FileViewHolder viewHolder = (FileViewHolder) v.getTag();
        if (viewHolder != null) {
            ArchosCheckBox checkbox = viewHolder.getCheckbox();
            if (mSelectedFiles.size() == 0 || !checkbox.isChecked()) {
                if (mSelectedFiles.contains(file)) {
                    mSelectedFiles.remove(file);
                }
                toggleItem(file, checkbox);
            }
        }
    }

}
