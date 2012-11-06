package com.quran.labs.androidquran.database;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.widget.Checkable;

import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarkTagTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.BookmarksTable;
import com.quran.labs.androidquran.database.BookmarksDBHelper.TagsTable;

public class BookmarksDBAdapter {

	private SQLiteDatabase mDb;
	private BookmarksDBHelper mDbHelper;
	
	public BookmarksDBAdapter(Context context) {
		mDbHelper = new BookmarksDBHelper(context);
	}

	public void open() throws SQLException {
      if (mDb == null){
		   mDb = mDbHelper.getWritableDatabase();
      }
	}

	public void close() {
      if (mDb != null){
		   mDbHelper.close();
         mDb = null;
      }
	}

   public List<Bookmark> getBookmarks(boolean loadTags){
      if (mDb == null){
         open();
         if (mDb == null){ return null; }
      }

      List<Bookmark> bookmarks = null;
      Cursor cursor = mDb.query(BookmarksTable.TABLE_NAME,
              null, null, null, null, null,
              BookmarksTable.ADDED_DATE + " DESC");
      if (cursor != null){
         bookmarks = new ArrayList<Bookmark>();
         while (cursor.moveToNext()){
            long id = cursor.getLong(0);
            Integer sura = cursor.getInt(1);
            Integer ayah = cursor.getInt(2);
            int page = cursor.getInt(3);
            long time = cursor.getLong(4);

            if (sura == 0 || ayah == 0){
               sura = null;
               ayah = null;
            }

            Bookmark bookmark = new Bookmark(id, sura, ayah, page, time);
            if (loadTags) {
               bookmark.mTags = getBookmarkTags(id);
            }
            bookmarks.add(bookmark);
         }
         cursor.close();
      }
      return bookmarks;
   }
   
   public List<Long> getBookmarkTagIds(long bookmarkId) {
      List<Long> bookmarkTags = null;
      Cursor cursor = mDb.query(BookmarkTagTable.TABLE_NAME,
            new String[] {BookmarkTagTable.TAG_ID},
            BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId,
            null, null, null, BookmarkTagTable.TAG_ID + " ASC");
      if (cursor != null){
         bookmarkTags = new ArrayList<Long>();
         while (cursor.moveToNext()){
            bookmarkTags.add(cursor.getLong(0));
         }
         cursor.close();
      }
      return bookmarkTags.size() > 0 ? bookmarkTags : null;
   }
   
   public List<Tag> getBookmarkTags(long bookmarkId) {
      List<Tag> bookmarkTags = null;
      Cursor cursor = mDb.query(BookmarkTagTable.TABLE_NAME,
              new String[] {BookmarkTagTable.TAG_ID},
              BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId,
              null, null, null, BookmarkTagTable.TAG_ID + " ASC");
      // TODO Use join to get tag name as well
      if (cursor != null){
         bookmarkTags = new ArrayList<Tag>();
         while (cursor.moveToNext()){
            long id = cursor.getLong(0);
            bookmarkTags.add(new Tag(id, null));
         }
         cursor.close();
      }
      return bookmarkTags.size() > 0 ? bookmarkTags : null;
   }

   /** @return final bookmarked state after calling this method */
   public boolean togglePageBookmark(int page) {
      long bookmarkId = getBookmarkId(null, null, page);
      if (bookmarkId < 0) {
         addBookmark(page);
         return true;
      } else {
         removeBookmark(bookmarkId);
         return false;
      }
   }
   
   public boolean isPageBookmarked(int page){
      return getBookmarkId(null, null, page) >= 0;
   }

   public long getBookmarkId(Integer sura, Integer ayah, int page) {
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }
      
      Cursor cursor = mDb.query(BookmarksTable.TABLE_NAME,
              null, BookmarksTable.PAGE + "=" + page + " AND " +
              BookmarksTable.SURA + (sura==null?" IS NULL":"="+sura) + " AND " +
              BookmarksTable.AYAH + (ayah==null?" IS NULL":"="+ayah), null, null, null, null);
      if (cursor.moveToFirst()){
         return cursor.getLong(0);
      }
      return -1;
   }
   
   public boolean isTagged(long bookmarkId){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }
      Cursor cursor = mDb.query(BookmarkTagTable.TABLE_NAME,
            null, BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId,
            null, null, null, null);
      return cursor.moveToFirst();
   }
   
   public long addBookmark(int page){
      return addBookmark(null, null, page);
   }

   public long addBookmark(int sura, int ayah, int page){
      return addBookmark(sura, ayah, page);
   }

   public long addBookmarkIfNotExists(Integer sura, Integer ayah, int page){
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }
      long bookmarkId = getBookmarkId(sura, ayah, page);
      if (bookmarkId < 0)
         bookmarkId = addBookmark(sura, ayah, page);
      return bookmarkId;
   }
   
   public long addBookmark(Integer sura, Integer ayah, int page){
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }

//      if (category != null && category == 0){
//         category = null;
//      }

      ContentValues values = new ContentValues();
      values.put(BookmarksTable.SURA, sura);
      values.put(BookmarksTable.AYAH, ayah);
      values.put(BookmarksTable.PAGE, page);
//      values.put(BookmarksTable.CATEGORY_ID, category);
      return mDb.insert(BookmarksTable.TABLE_NAME, null, values);
   }

   public boolean removeBookmark(long bookmarkId){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }
      clearBookmarkTags(bookmarkId);
      return mDb.delete(BookmarksTable.TABLE_NAME,
              BookmarksTable.ID + "=" + bookmarkId, null) == 1;
   }

   public List<Tag> getTags(){
      if (mDb == null){
         open();
         if (mDb == null){ return null; }
      }

      List<Tag> tags = null;
      Cursor cursor = mDb.query(TagsTable.TABLE_NAME,
              null, null, null, null, null,
              TagsTable.NAME + " ASC");
      if (cursor != null){
         tags = new ArrayList<Tag>();
         while (cursor.moveToNext()){
            long id = cursor.getLong(0);
            String name = cursor.getString(1);
            Tag tag = new Tag(id, name);
            tags.add(tag);
         }
         cursor.close();
      }
      return tags;
   }

   public long addTag(String name){
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }

      ContentValues values = new ContentValues();
      values.put(TagsTable.NAME, name);
      return mDb.insert(TagsTable.TABLE_NAME, null, values);
   }

   public boolean updateTag(long id, String newName){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }

      ContentValues values = new ContentValues();
      values.put(TagsTable.ID, id);
      values.put(TagsTable.NAME, newName);
      return 1 == mDb.update(TagsTable.TABLE_NAME, values,
            TagsTable.ID + "=" + id, null);
   }

   public boolean removeTag(long tagId, boolean removeBookmarks){
      if (mDb == null){
         open();
         if (mDb == null){ return false; }
      }

      boolean removed = mDb.delete(TagsTable.TABLE_NAME,
            TagsTable.ID + "=" + tagId, null) == 1;
/*      if (removeBookmarks){
         mDb.delete(BookmarksTable.TABLE_NAME,
                 BookmarksTable.CATEGORY_ID + "=" + categoryId, null);
      }
      else {
         mDb.rawQuery("UPDATE " + BookmarksTable.TABLE_NAME + " SET " +
                 BookmarksTable.CATEGORY_ID + " = NULL WHERE " +
                 BookmarksTable.CATEGORY_ID + " = " + categoryId, null);
      }
*/
      return removed;
   }
   
//   public List<Tag> getBookmarkTags(long bookmarkId) {
//      // TODO
//      return null;
//   }
   
   public long getBookmarkTagId(long bookmarkId, long tagId) {
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }
      
      Cursor cursor = mDb.query(BookmarkTagTable.TABLE_NAME, null,
            BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId + " AND "
                  + BookmarkTagTable.TAG_ID + "=" + tagId, null, null, null, null);
      if (cursor.moveToFirst()){
         return cursor.getLong(0);
      }
      return -1;
   }
   
   public void tagBookmark(long bookmarkId, List<Tag> tags) {
      if (mDb == null){
         open();
         if (mDb == null){ return; }
      }
      
      // TODO write efficient SQL instead of this silly loop
      for (Tag t : tags) {
         if (t.mId < 0)
            continue;
         long id = getBookmarkTagId(bookmarkId, t.mId);
         if (id < 0 && t.isChecked()) {
            ContentValues values = new ContentValues();
            values.put(BookmarkTagTable.BOOKMARK_ID, bookmarkId);
            values.put(BookmarkTagTable.TAG_ID, t.mId);
            id = mDb.insert(BookmarkTagTable.TABLE_NAME, null, values);
         } else if (id >= 0 && !t.isChecked()) {
            mDb.delete(BookmarkTagTable.TABLE_NAME,
                  BookmarkTagTable.ID + "=" + id, null);
         }
      }
   }
   
   public long tagBookmark(long bookmarkId, long tagId) {
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }
      
      long id = getBookmarkTagId(bookmarkId, tagId);
      if (id < 0) {
         ContentValues values = new ContentValues();
         values.put(BookmarkTagTable.BOOKMARK_ID, bookmarkId);
         values.put(BookmarkTagTable.TAG_ID, tagId);
         id = mDb.insert(BookmarkTagTable.TABLE_NAME, null, values);
      }
      return id;
   }
   
   public void untagBookmark(long bookmarkId, long tagId) {
      if (mDb == null){
         open();
         if (mDb == null){ return; }
      }
      mDb.delete(BookmarkTagTable.TABLE_NAME,
            BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId + " AND "
                  + BookmarkTagTable.TAG_ID + "=" + tagId, null);
   }
   
   public int clearBookmarkTags(long bookmarkId) {
      if (mDb == null){
         open();
         if (mDb == null){ return -1; }
      }
      
      return mDb.delete(BookmarkTagTable.TABLE_NAME,
            BookmarkTagTable.BOOKMARK_ID + "=" + bookmarkId, null);
   }
   
   public static class Tag implements Checkable {
      public long mId;
      public String mName;
      public boolean mChecked = false;
      
      public Tag(long id, String name) {
         mId = id;
         mName = name;
      }

      @Override
      public String toString() {
         return mName == null? super.toString() : mName;
      }

      @Override
      public boolean isChecked() {
         return mChecked;
      }

      @Override
      public void setChecked(boolean checked) {
         mChecked = checked;
      }

      @Override
      public void toggle() {
         mChecked = !mChecked;
      }
   }
   
	public static class Bookmark {
		public long mId;
      public Integer mSura;
      public Integer mAyah;
      public int mPage;
      public long mTimestamp;
      public List<Tag> mTags;

      public Bookmark(long id, Integer sura, Integer ayah, int page, long timestamp){
         mId = id;
         mSura = sura;
         mAyah = ayah;
         mPage = page;
         mTimestamp = timestamp;
      }
      
      public boolean isPageBookmark() {
         return mSura == null && mAyah == null;
      }
      
	}
}