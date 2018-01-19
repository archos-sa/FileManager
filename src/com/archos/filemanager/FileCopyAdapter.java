package com.archos.filemanager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.archos.filecorelibrary.MetaFile2;

import android.content.Context;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FileCopyAdapter extends RecyclerView.Adapter<FileCopyAdapter.ViewHolder> {
    private HashMap<MetaFile2, Long> mProgress;
    private Context mContext;
    private List<MetaFile2> mOrderedPastingFiles;

 
    public static class ViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout mTextView;
        public TextView filename;
        public ImageView mIconView;
        public ViewHolder(LinearLayout v) {
            super(v);
            mTextView = v;
        }
    }

    public FileCopyAdapter(HashMap<MetaFile2, Long> progress, Context context) {        
        mProgress = progress;
        mOrderedPastingFiles = new ArrayList<MetaFile2>();
        mContext = context;
    }
    public void setProgress(HashMap<MetaFile2, Long> progress){
        mProgress = progress;
    }

    @Override
    public FileCopyAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                   int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                               .inflate(R.layout.file_copy_item, parent, false);
        // set the view's size, margins, paddings and layout parameters
        ViewHolder vh = new ViewHolder((LinearLayout) v);
        vh.filename =  (TextView) v.findViewById(R.id.filename);
        vh.mIconView = (ImageView)v.findViewById(R.id.icon);
        
        return vh;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if(mOrderedPastingFiles!=null&& mOrderedPastingFiles.size() >position){
            List<MetaFile2> keys = mOrderedPastingFiles;
            MetaFile2 file =  keys.get(position);
            holder.filename.setText(file.getName());
            if(file.isFile())
             holder.mIconView.setImageResource(FileManagerUtils.getMimeTypeIconResId(file.getMimeType(), file.getExtension()));
            else
                holder.mIconView.setImageResource(R.drawable.filetype_folder);
            ColorDrawable c = new ColorDrawable(mContext.getResources().getColor(R.color.background_progress));
            ClipDrawable d = new ClipDrawable(c, Gravity.LEFT, ClipDrawable.HORIZONTAL);
            if(mProgress.get(file)!=null)
                d.setLevel(mProgress.get(file)==-1?10000:(int)((float)mProgress.get(file)/(float)file.getComputedLength()*10000.0));
            holder.mTextView.setBackgroundDrawable(d);
        }
    }

    @Override
    public int getItemCount() {
        return mOrderedPastingFiles.size();
    }
    public void setOrderedFiles(List<MetaFile2> orderedPastingFiles) {  
        mOrderedPastingFiles = orderedPastingFiles;
    }


}
