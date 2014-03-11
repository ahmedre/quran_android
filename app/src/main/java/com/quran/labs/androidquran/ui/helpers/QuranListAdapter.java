package com.quran.labs.androidquran.ui.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

import java.util.HashMap;


public class QuranListAdapter extends BaseAdapter {

   private Context mContext;
   private LayoutInflater mInflater;
   private QuranRow[] mElements;
   private int mLayout;
   private boolean mReshapeArabic;
   private boolean mUseArabicFont;
   private HashMap<Integer, BitmapDrawable> AudioStatuses ;
   public QuranListAdapter(Context context, int layout, QuranRow[] items){
      mInflater = LayoutInflater.from(context);
      mElements = items;
      mLayout = layout;
      mContext = context;

      // should we reshape if we have arabic?
      mUseArabicFont = QuranSettings.needArabicFont(context);
      mReshapeArabic = QuranSettings.isReshapeArabic(context);

       AudioStatuses= new  HashMap<Integer, BitmapDrawable>();

       AudioStatuses.put(R.drawable.audio_full,createBackground(R.drawable.audio_full));
       AudioStatuses.put(R.drawable.audio_partial,createBackground(R.drawable.audio_partial));
       AudioStatuses.put(R.drawable.audio_none,createBackground(R.drawable.audio_none));

   }

    private BitmapDrawable createBackground(int audio_status) {
        BitmapDrawable TileMe = new BitmapDrawable(BitmapFactory.decodeResource(mContext.getResources(), audio_status));
        TileMe.setGravity(Gravity.RIGHT  ); // Bottom

       // TileMe.setTileModeX(Shader.TileMode);
        return TileMe;
    }

    public int getCount() {
      return mElements.length;
   }

   public Object getItem(int position) {
      return mElements[position];
   }

   public long getItemId(int position) {
      return position;
   }

   public void setElements(QuranRow[] elements){
      mElements = elements;
   }

   public View getView(final int position, View convertView, ViewGroup parent) {
      ViewHolder holder;

      if (convertView == null) {
         convertView = mInflater.inflate(mLayout, null);
         holder = new ViewHolder();
         holder.text = (TextView)convertView.findViewById(R.id.suraName);
         holder.metadata = (TextView)convertView.findViewById(R.id.suraDetails);
         holder.page = (TextView)convertView.findViewById(R.id.pageNumber);
         holder.number = (TextView)convertView.findViewById(R.id.suraNumber);
         holder.header = (TextView)convertView.findViewById(R.id.headerName);
         holder.image = (TextView)convertView.findViewById(R.id.rowIcon);

         if (mUseArabicFont){
            Typeface typeface = ArabicStyle.getTypeface(mContext);
            holder.text.setTypeface(typeface);
            holder.metadata.setTypeface(typeface);
            holder.header.setTypeface(typeface);
         }
         convertView.setTag(holder);
      }
      else { holder = (ViewHolder) convertView.getTag(); }

      QuranRow item = mElements[position];
      String text = item.text;
      if (mReshapeArabic){
         text = ArabicStyle.reshape(mContext, text);
      }

      holder.page.setText(QuranUtils.getLocalizedNumber(mContext, item.page));
      holder.text.setText(text);

      holder.header.setText(text);
      holder.number.setText(
              QuranUtils.getLocalizedNumber(mContext, item.sura));

      int color = R.color.sura_details_color;
      if (mElements[position].isHeader()){
         holder.text.setVisibility(View.GONE);
         holder.header.setVisibility(View.VISIBLE);
         holder.metadata.setVisibility(View.GONE);
         holder.number.setVisibility(View.GONE);
         holder.image.setVisibility(View.GONE);
         color = R.color.header_text_color;
      }
      else {
         String info = item.metadata;
         holder.metadata.setVisibility(View.VISIBLE);
         holder.text.setVisibility(View.VISIBLE);
         holder.header.setVisibility(View.GONE);
         holder.metadata.setText(ArabicStyle.reshape(mContext, info));

         if (item.imageResource == null){
            holder.number.setVisibility(View.VISIBLE);
            holder.image.setVisibility(View.GONE);
         }
         else {
            holder.image.setBackgroundResource(item.imageResource);
            holder.image.setText(item.imageText);
            holder.image.setVisibility(View.VISIBLE);
            holder.number.setVisibility(View.GONE);
         }

          if (item.AudioimageResource!=null)
          {
              holder.text.setBackgroundDrawable(AudioStatuses.get(item.AudioimageResource));
          }
      }
      holder.page.setTextColor(mContext.getResources().getColor(color));
      int pageVisibility = item.page == 0? View.GONE : View.VISIBLE;
      holder.page.setVisibility(pageVisibility);
      
      // If the row is checked (for CAB mode), theme its bg appropriately
      if (parent != null && parent instanceof ListView){
         int background = ((ListView)parent).isItemChecked(position)?
                 R.drawable.abs__list_activated_holo : 0;
         convertView.setBackgroundResource(background);
      }
      
      return convertView;
   }

   class ViewHolder {
      TextView text;
      TextView page;
      TextView number;
      TextView metadata;
      TextView header;
      TextView image;
   }
}
