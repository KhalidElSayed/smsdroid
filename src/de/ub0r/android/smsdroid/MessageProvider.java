/*
 * Copyright (C) 2010 Felix Bechstein, The Android Open Source Project
 * 
 * This file is part of SMSdroid.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.android.smsdroid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.CallLog.Calls;
import android.text.TextUtils;
import de.ub0r.android.lib.DbUtils;
import de.ub0r.android.lib.Log;

/**
 * @author flx
 */
public final class MessageProvider extends ContentProvider {
	/** Tag for output. */
	private static final String TAG = "msgp";

	/** Name of the {@link SQLiteDatabase}. */
	private static final String DATABASE_NAME = "mms.db";
	/** Version of the {@link SQLiteDatabase}. */
	private static final int DATABASE_VERSION = 20;

	/** Size of buffer for fetching parts. */
	private static final int BUFFSIZE = 1024;

	/** Authority. */
	public static final String AUTHORITY = "de.ub0r.android.smsdroid."
			+ "messages";

	/**
	 * Class holding all messages.
	 * 
	 * @author flx
	 */
	public static final class Messages {
		/** Table name. */
		private static final String TABLE = "messages";
		/** {@link HashMap} for projection. */
		private static final HashMap<String, String> PROJECTION_MAP;

		/** Bitmap showing the play button. */
		public static final Bitmap BITMAP_PLAY = Bitmap.createBitmap(1, 1,
				Config.RGB_565);

		/** INDEX: id. */
		public static final int INDEX_ID = 0;
		/** INDEX: date. */
		public static final int INDEX_DATE = 1;
		/** INDEX: address. */
		public static final int INDEX_ADDRESS = 2;
		/** INDEX: thread_id. */
		public static final int INDEX_THREADID = 3;
		/** INDEX: body. */
		public static final int INDEX_BODY = 4;
		/** INDEX: type. */
		public static final int INDEX_TYPE = 5;
		/** INDEX: read. */
		public static final int INDEX_READ = 6;
		/** INDEX: subject. */
		public static final int INDEX_SUBJECT = 7;

		/** Id. */
		public static final String ID = BaseColumns._ID;
		/** Date. */
		public static final String DATE = Calls.DATE;
		/** Address. */
		public static final String ADDRESS = "address";
		/** thread_id. */
		public static final String THREADID = "thread_id";
		/** body. */
		public static final String BODY = "body";
		/** type. */
		public static final String TYPE = Calls.TYPE;
		/** read. */
		public static final String READ = "read";
		/** Subject. */
		public static final String SUBJECT = "subject";

		/** Type for incoming sms. */
		public static final int TYPE_SMS_IN = Calls.INCOMING_TYPE;
		/** Type for outgoing sms. */
		public static final int TYPE_SMS_OUT = Calls.OUTGOING_TYPE;
		/** Type for sms drafts. */
		public static final int TYPE_SMS_DRAFT = 3;
		/** Type for incoming mms. */
		public static final int TYPE_MMS_IN = 132;
		/** Type for outgoing mms. */
		public static final int TYPE_MMS_OUT = 128;
		/** Type for mms drafts. */
		// public static final int MMS_DRAFT = 128;
		/** Type for not yet loaded mms. */
		public static final int TYPE_MMS_TOLOAD = 130;

		/** Where clause matching sms. */
		public static final String WHERE_TYPE_SMS = TYPE + " < 100";
		/** Where clause matching sms. */
		public static final String WHERE_TYPE_MMS = TYPE + " > 100";

		/** {@link Cursor}'s projection. */
		public static final String[] PROJECTION = { //
		ID, // 0
				DATE, // 1
				ADDRESS, // 2
				THREADID, // 3
				BODY, // 4
				TYPE, // 5
				READ, // 6
				SUBJECT, // 7
		};

		/** Index for parts: id. */
		public static final int PARTS_INDEX_ID = 0;
		/** Index for parts: mid. */
		public static final int PARTS_INDEX_MID = 1;
		/** Index for parts: ct. */
		public static final int PARTS_INDEX_CT = 2;

		/** id. */
		public static final String PARTS_ID = "_id";
		/** mid. */
		public static final String PARTS_MID = "mid";
		/** ct. */
		public static final String PARTS_CT = "ct";
		/** text. */
		public static final String PARTS_TEXT = "text";

		/** {@link Uri} for parts. */
		private static final Uri URI_PARTS = Uri.parse("content://mms/part/");

		/** Cursor's projection for parts. */
		public static final String[] PROJECTION_PARTS = { //
		PARTS_ID, // 0
				PARTS_MID, // 1
				PARTS_CT, // 2
		};

		/** Remote {@link Cursor}'s projection. */
		public static final String[] ORIG_PROJECTION_SMS = PROJECTION;

		/** Remote {@link Cursor}'s projection. */
		// public static final String[] ORIG_PROJECTION_MMS = new String[] { //
		// .
		// ID, // 0
		// DATE, // 1
		// THREADID, // 2
		// READ, // 3
		// TYPE, // 4
		// };

		/** Default sort order. */
		public static final String DEFAULT_SORT_ORDER = DATE + " ASC";

		/** Content {@link Uri}. */
		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ AUTHORITY + "/messages");
		/** Content {@link Uri}. */
		public static final Uri CACHE_URI = Uri.parse("content://" + AUTHORITY
				+ "/cache/messages");
		/** Content {@link Uri}. */
		public static final Uri CACHE_THREADS_URI = Uri.parse("content://"
				+ AUTHORITY + "/cache/conversations");
		/** Content {@link Uri}. */
		public static final Uri THREAD_URI = Uri.parse("content://" + AUTHORITY
				+ "/conversation");
		/** {@link Uri} of real sms database. */
		public static final Uri ORIG_URI_SMS = Uri.parse("content://sms/");
		/** {@link Uri} of real mms database. */
		public static final Uri ORIG_URI_MMS = Uri.parse("content://mms/");

		/** SQL WHERE: unread messages. */
		static final String SELECTION_UNREAD = "read = '0'";
		/** SQL WHERE: read messages. */
		static final String SELECTION_READ = "read = '1'";

		/**
		 * The MIME type of {@link #CONTENT_URI} providing a list of threads.
		 */
		public static final String CONTENT_TYPE = // .
		"vnd.android.cursor.dir/vnd.ub0r.message";

		/**
		 * The MIME type of a {@link #CONTENT_URI} single thread.
		 */
		public static final String CONTENT_ITEM_TYPE = // .
		"vnd.android.cursor.item/vnd.ub0r.message";

		static {
			PROJECTION_MAP = new HashMap<String, String>();
			final int l = PROJECTION.length;
			for (int i = 0; i < l; i++) {
				PROJECTION_MAP.put(PROJECTION[i], PROJECTION[i]);
			}
		}

		/**
		 * Create table in {@link SQLiteDatabase}.
		 * 
		 * @param db
		 *            {@link SQLiteDatabase}
		 */
		public static void onCreate(final SQLiteDatabase db) {
			Log.i(TAG, "create table: " + TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + TABLE);
			db.execSQL("CREATE TABLE " + TABLE + " (" // .
					+ ID + " LONG," // .
					+ DATE + " LONG,"// .
					+ ADDRESS + " TEXT,"// .
					+ THREADID + " LONG,"// .
					+ BODY + " TEXT,"// .
					+ TYPE + " INTEGER," // .
					+ READ + " INTEGER," // .
					+ SUBJECT + " TEXT" // .
					+ ");");
		}

		/**
		 * Upgrade table.
		 * 
		 * @param db
		 *            {@link SQLiteDatabase}
		 * @param oldVersion
		 *            old version
		 * @param newVersion
		 *            new version
		 */
		public static void onUpgrade(final SQLiteDatabase db,
				final int oldVersion, final int newVersion) {
			Log.w(TAG, "Upgrading table: " + TABLE);
			onCreate(db);
		}

		/**
		 * Get {@link Cursor} holding MMS parts for mid.
		 * 
		 * @param cr
		 *            {@link ContentResolver}
		 * @param mid
		 *            message's id
		 * @return {@link Cursor}
		 */
		private static Cursor getPartsCursor(final ContentResolver cr, // .
				final long mid) {
			return cr.query(URI_PARTS, null, PARTS_MID + " = " + -1L * mid,
					null, null);
		}

		/**
		 * Get MMS parts as {@link Object}s (Bitmap, {@link CharSequence},
		 * {@link Intent}).
		 * 
		 * @param cr
		 *            {@link ContentResolver}
		 * @param mid
		 *            MMS's id
		 * @return {@link ArrayList} of parts
		 */
		public static ArrayList<Object> getParts(final ContentResolver cr,
				final long mid) {
			Cursor cursor = getPartsCursor(cr, mid);
			if (cursor == null || !cursor.moveToFirst()) {
				return null;
			}
			final ArrayList<Object> ret = new ArrayList<Object>();
			final int iID = cursor.getColumnIndex(PARTS_ID);
			final int iCT = cursor.getColumnIndex(PARTS_CT);
			final int iText = cursor.getColumnIndex(PARTS_TEXT);
			do {
				final long pid = cursor.getLong(iID);
				final String ct = cursor.getString(iCT);
				Log.d(TAG, "part: " + pid + " " + ct);

				// get part
				InputStream is = getPart(cr, pid);
				if (is == null) {
					Log.i(TAG, "InputStream for part " + pid + " is null");
					if (iText >= 0 && ct.startsWith("text/")) {
						ret.add(cursor.getString(iText));
					}
					continue;
				}
				if (ct == null) {
					continue;
				}
				final Intent i = getPartContentIntent(pid, ct);
				if (i != null) {
					ret.add(i);
				}
				final Object o = getPartObject(ct, is);
				if (o != null) {
					ret.add(o);
				}

				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						Log.e(TAG, "Failed to close stream", e);
					} // Ignore
				}
			} while (cursor.moveToNext());
			return ret;
		}

		/**
		 * Get MMS part's {@link Uri}.
		 * 
		 * @param pid
		 *            part's id
		 * @return {@link Uri}
		 */
		private static Uri getPartUri(final long pid) {
			return ContentUris.withAppendedId(URI_PARTS, pid);
		}

		/**
		 * Get content's {@link Intent} for MMS part.
		 * 
		 * @param pid
		 *            part's id
		 * @param ct
		 *            part's content type
		 * @return {@link Intent}
		 */
		private static Intent getPartContentIntent(final long pid,
				final String ct) {
			if (ct.startsWith("image/")) {
				final Intent i = new Intent(Intent.ACTION_VIEW);
				i.setDataAndType(getPartUri(pid), ct);
				i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
				return i;
			} else if (ct.startsWith("video/") || ct.startsWith("audio/")) {
				final Intent i = new Intent(Intent.ACTION_VIEW);
				i.setDataAndType(getPartUri(pid), ct);
				return i;
			}
			return null;
		}

		/**
		 * Get MMS part's picture or text.
		 * 
		 * @param ct
		 *            part's content type
		 * @param is
		 *            {@link InputStream}
		 * @return {@link Bitmap} or {@link CharSequence}
		 */
		private static Object getPartObject(final String ct, // .
				final InputStream is) {
			if (is == null) {
				return null;
			}
			if (ct.startsWith("image/")) {
				return BitmapFactory.decodeStream(is);
			} else if (ct.startsWith("video/") || ct.startsWith("audio/")) {
				return BITMAP_PLAY;
			} else if (ct.startsWith("text/")) {
				return fetchPart(is);
			}
			return null;
		}

		/**
		 * Fetch a part.
		 * 
		 * @param is
		 *            {@link InputStream}
		 * @return part
		 */
		private static CharSequence fetchPart(final InputStream is) {
			Log.d(TAG, "fetchPart(cr, is)");
			String ret = null;
			// get part
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				byte[] buffer = new byte[BUFFSIZE];
				int len = is.read(buffer);
				while (len >= 0) {
					baos.write(buffer, 0, len);
					len = is.read(buffer);
				}
				ret = new String(baos.toByteArray());
				Log.d(TAG, ret);
			} catch (IOException e) {
				Log.e(TAG, "Failed to load part data", e);
			} finally {
				if (is != null) {
					try {
						is.close();
					} catch (IOException e) {
						Log.e(TAG, "Failed to close stream", e);
					} // Ignore
				}
			}
			return ret;
		}

		/**
		 * Get MMS part as {@link InputStream}.
		 * 
		 * @param cr
		 *            {@link ContentResolver}
		 * @param pid
		 *            part's id
		 * @return {@link InputStream}
		 */
		private static InputStream getPart(final ContentResolver cr,
				final long pid) {
			InputStream is = null;
			final Uri uri = getPartUri(pid);
			try {
				is = cr.openInputStream(uri);
			} catch (IOException e) {
				Log.e(TAG, "Failed to load part data", e);
			}
			return is;
		}

		/** Default constructor. */
		private Messages() {
			// nothing here.
		}
	}

	/**
	 * Class holding all threads.
	 * 
	 * @author flx
	 */
	public static final class Threads {
		/** Table name. */
		private static final String TABLE = "threads";
		/** {@link HashMap} for projection. */
		private static final HashMap<String, String> PROJECTION_MAP;

		/** No photo available. */
		public static final Bitmap NO_PHOTO = Bitmap.createBitmap(1, 1,
				Bitmap.Config.RGB_565);

		/** INDEX: id. */
		public static final int INDEX_ID = 0;
		/** INDEX: date. */
		public static final int INDEX_DATE = 1;
		/** INDEX: address. */
		public static final int INDEX_ADDRESS = 2;
		/** INDEX: body. */
		public static final int INDEX_BODY = 3;
		/** INDEX: type. */
		public static final int INDEX_TYPE = 4;
		/** INDEX: person id. */
		public static final int INDEX_PID = 5;
		/** INDEX: name. */
		public static final int INDEX_NAME = 6;
		/** INDEX: count. */
		public static final int INDEX_COUNT = 7;
		/** INDEX: read. */
		public static final int INDEX_READ = 8;

		/** Id. */
		public static final String ID = BaseColumns._ID;
		/** Date. */
		public static final String DATE = Calls.DATE;
		/** Address. */
		public static final String ADDRESS = "address";
		/** body. */
		public static final String BODY = "body";
		/** type. */
		public static final String TYPE = Calls.TYPE;
		/** person id. */
		public static final String PID = "pid";
		/** name. */
		public static final String NAME = "name";
		/** count. */
		public static final String COUNT = "count";
		/** read. */
		public static final String READ = "read";

		/** Cursor's projection. */
		public static final String[] PROJECTION = { //
		ID, // 0
				DATE, // 1
				ADDRESS, // 2
				BODY, // 3
				TYPE, // 4
				PID, // 5
				NAME, // 6
				COUNT, // 7
				READ, // 8
		};

		/** ORIG_URI to resolve. */
		public static final Uri ORIG_URI = Uri
				.parse("content://mms-sms/conversations/");

		/** Default sort order. */
		public static final String DEFAULT_SORT_ORDER = DATE + " DESC";

		/** Content {@link Uri}. */
		public static final Uri CONTENT_URI = Uri.parse("content://"
				+ AUTHORITY + "/threads");
		/** Content {@link Uri}. */
		public static final Uri CACHE_URI = Uri.parse("content://" + AUTHORITY
				+ "/cache/threads");

		/**
		 * The MIME type of {@link #CONTENT_URI} providing a list of threads.
		 */
		public static final String CONTENT_TYPE = // .
		"vnd.android.cursor.dir/vnd.ub0r.thread";

		/**
		 * The MIME type of a {@link #CONTENT_URI} single thread.
		 */
		public static final String CONTENT_ITEM_TYPE = // .
		"vnd.android.cursor.item/vnd.ub0r.thread";

		static {
			PROJECTION_MAP = new HashMap<String, String>();
			final int l = PROJECTION.length;
			for (int i = 0; i < l; i++) {
				PROJECTION_MAP.put(PROJECTION[i], PROJECTION[i]);
			}
		}

		/**
		 * Create table in {@link SQLiteDatabase}.
		 * 
		 * @param db
		 *            {@link SQLiteDatabase}
		 */
		public static void onCreate(final SQLiteDatabase db) {
			Log.i(TAG, "create table: " + TABLE);
			db.execSQL("DROP TABLE IF EXISTS " + TABLE);
			db.execSQL("CREATE TABLE " + TABLE + " (" // .
					+ ID + " LONG," // .
					+ DATE + " LONG,"// .
					+ ADDRESS + " TEXT,"// .
					+ BODY + " TEXT,"// .
					+ TYPE + " INTEGER," // .
					+ PID + " TEXT," // .
					+ NAME + " TEXT,"// .
					+ COUNT + " INTEGER," // .
					+ READ + " INTEGER" // .
					+ ");");
		}

		/**
		 * Upgrade table.
		 * 
		 * @param db
		 *            {@link SQLiteDatabase}
		 * @param oldVersion
		 *            old version
		 * @param newVersion
		 *            new version
		 */
		public static void onUpgrade(final SQLiteDatabase db,
				final int oldVersion, final int newVersion) {
			Log.w(TAG, "Upgrading table: " + TABLE);
			onCreate(db);
		}

		/** Default constructor. */
		private Threads() {
			// nothing here.
		}
	}

	/** Internal id: threads. */
	private static final int THREADS = 1;
	/** Internal id: single thread. */
	private static final int THREAD_ID = 2;
	/** Internal id: messages. */
	private static final int MESSAGES = 3;
	/** Internal id: single message. */
	private static final int MESSAGE_ID = 4;
	/** Internal id: messages from a single thread. */
	private static final int MESSAGES_TID = 5;
	/** Internal id: cache for messages. */
	private static final int MESSAGES_CACHE = 6;
	/** Internal id: cache for threads. */
	private static final int THREADS_CACHE = 7;
	/** Internal id: cache for conversations. */
	private static final int CONVERSATIONS_CACHE = 8;

	/** {@link UriMatcher}. */
	private static final UriMatcher URI_MATCHER;

	static {
		URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URI_MATCHER.addURI(AUTHORITY, "threads", THREADS);
		URI_MATCHER.addURI(AUTHORITY, "threads/#", THREAD_ID);
		URI_MATCHER.addURI(AUTHORITY, "messages", MESSAGES);
		URI_MATCHER.addURI(AUTHORITY, "messages/#", MESSAGE_ID);
		URI_MATCHER.addURI(AUTHORITY, "conversation/#", MESSAGES_TID);
		URI_MATCHER.addURI(AUTHORITY, "cache/threads", THREADS_CACHE);
		URI_MATCHER.addURI(AUTHORITY, "cache/messages", MESSAGES_CACHE);
		URI_MATCHER.addURI(AUTHORITY, "cache/conversations",
				CONVERSATIONS_CACHE);
	}

	/**
	 * This class helps open, create, and upgrade the database file.
	 */
	private static class DatabaseHelper extends SQLiteOpenHelper {

		/**
		 * Default Constructor.
		 * 
		 * @param context
		 *            {@link Context}
		 */
		DatabaseHelper(final Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onCreate(final SQLiteDatabase db) {
			Log.i(TAG, "create database");
			Threads.onCreate(db);
			Messages.onCreate(db);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onUpgrade(final SQLiteDatabase db, final int oldVersion,
				final int newVersion) {
			Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
					+ newVersion + ", which will destroy all old data");
			Threads.onUpgrade(db, oldVersion, newVersion);
			Messages.onUpgrade(db, oldVersion, newVersion);
		}
	}

	/** {@link DatabaseHelper}. */
	private DatabaseHelper mOpenHelper;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int delete(final Uri uri, final String selection,
			final String[] selectionArgs) {
		Log.d(TAG, "delete: " + uri + " w: " + selection);
		final SQLiteDatabase db = this.mOpenHelper.getWritableDatabase();
		final ContentResolver cr = this.getContext().getContentResolver();
		int ret = 0;
		switch (URI_MATCHER.match(uri)) {
		case MESSAGES_CACHE:
			ret = db.delete(Messages.TABLE, selection, selectionArgs);
			break;
		case THREADS_CACHE:
			ret = db.delete(Threads.TABLE, selection, selectionArgs);
			break;
		case MESSAGES:
			ret = db.delete(Messages.TABLE, selection, selectionArgs);
			cr.delete(Messages.ORIG_URI_SMS, selection, selectionArgs);
			cr.delete(Messages.ORIG_URI_MMS, selection, selectionArgs);
			SyncService.syncThreads(this.getContext(), -1L);
			break;
		case MESSAGE_ID:
			final long mid = ContentUris.parseId(uri);
			long tid = -1L;
			Cursor c = db.query(Messages.TABLE,
					new String[] { Messages.THREADID },
					Messages.ID + "=" + mid, null, null, null, null);
			if (c != null && c.moveToFirst()) {
				tid = c.getLong(0);
			}
			if (c != null && !c.isClosed()) {
				c.close();
			}
			ret = db.delete(Messages.TABLE, DbUtils.sqlAnd(Messages.ID + "="
					+ mid, selection), selectionArgs);
			if (mid >= 0L) {
				cr.delete(ContentUris
						.withAppendedId(Messages.ORIG_URI_SMS, mid), selection,
						selectionArgs);
			} else {
				cr.delete(ContentUris.withAppendedId(Messages.ORIG_URI_MMS, -1L
						* mid), selection, selectionArgs);
			}
			SyncService.syncThreads(this.getContext(), tid);
			break;
		case MESSAGES_TID:
		case THREAD_ID:
			tid = ContentUris.parseId(uri);
			ret = db.delete(Messages.TABLE, DbUtils.sqlAnd(Messages.THREADID
					+ "=" + tid, selection), selectionArgs);
			ret += db.delete(Threads.TABLE, DbUtils.sqlAnd(Threads.ID + "="
					+ tid, selection), selectionArgs);
			cr.delete(ContentUris.withAppendedId(Threads.CONTENT_URI, tid),
					selection, selectionArgs);
			break;
		case THREADS:
			ret = db.delete(Threads.TABLE, selection, selectionArgs);
			cr.delete(Threads.CONTENT_URI, selection, selectionArgs);
			break;
		default:
			throw new IllegalArgumentException("Unknown Uri " + uri);
		}
		if (ret > 0) {
			cr.notifyChange(Messages.CONTENT_URI, null);
			SyncService.syncMessages(this.getContext());
		}
		Log.d(TAG, "exit delete(): " + ret);
		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getType(final Uri uri) {
		switch (URI_MATCHER.match(uri)) {
		case MESSAGES:
			return Messages.CONTENT_TYPE;
		case MESSAGE_ID:
			return Messages.CONTENT_ITEM_TYPE;
		case THREADS:
			return Threads.CONTENT_TYPE;
		case THREAD_ID:
			return Threads.CONTENT_ITEM_TYPE;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Uri insert(final Uri uri, final ContentValues values) {
		Log.d(TAG, "insert: " + uri + " " + values);
		final SQLiteDatabase db = this.mOpenHelper.getWritableDatabase();
		long ret = -1L;
		switch (URI_MATCHER.match(uri)) {
		case MESSAGES_CACHE:
			ret = db.insert(Messages.TABLE, null, values);
			break;
		case THREADS_CACHE:
			ret = db.insert(Threads.TABLE, null, values);
			break;
		default:
			throw new IllegalArgumentException("Unknown Uri " + uri);
		}
		Log.d(TAG, "exit insert(): " + ret);
		return ContentUris.withAppendedId(uri, ret);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onCreate() {
		this.mOpenHelper = new DatabaseHelper(this.getContext());
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Cursor query(final Uri uri, final String[] projection,
			final String selection, final String[] selectionArgs,
			final String sortOrder) {
		Log.d(TAG, "query: " + uri + " w: " + selection);
		final SQLiteDatabase db = this.mOpenHelper.getWritableDatabase();
		int uid = URI_MATCHER.match(uri);
		if (uid != CONVERSATIONS_CACHE && uid != MESSAGES_CACHE
				&& uid != THREADS_CACHE) {
			SyncService.syncMessages(this.getContext());
		}

		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		// If no sort order is specified use the default
		String orderBy = sortOrder;

		switch (uid) {
		case MESSAGE_ID:
			qb.appendWhere(Messages.ID + "=" + ContentUris.parseId(uri));
			qb.setTables(Messages.TABLE);
			qb.setProjectionMap(Messages.PROJECTION_MAP);
			if (TextUtils.isEmpty(orderBy)) {
				orderBy = Messages.DEFAULT_SORT_ORDER;
			}
			break;
		case MESSAGES_TID:
			qb.appendWhere(Messages.THREADID + "=" + ContentUris.parseId(uri));
			qb.setTables(Messages.TABLE);
			qb.setProjectionMap(Messages.PROJECTION_MAP);
			if (TextUtils.isEmpty(orderBy)) {
				orderBy = Messages.DEFAULT_SORT_ORDER;
			}
			break;
		case CONVERSATIONS_CACHE:
			if (TextUtils.isEmpty(orderBy)) {
				orderBy = Messages.DEFAULT_SORT_ORDER;
			}
			Log.d(TAG, "return Cursor directly");
			return db.query(Messages.TABLE, projection, selection,
					selectionArgs, Messages.THREADID, null, orderBy);
		case MESSAGES_CACHE:
			if (TextUtils.isEmpty(orderBy)) {
				orderBy = Messages.DEFAULT_SORT_ORDER;
			}
			Log.d(TAG, "return Cursor directly");
			return db.query(Messages.TABLE, projection, selection,
					selectionArgs, null, null, orderBy);
		case MESSAGES:
			qb.setTables(Messages.TABLE);
			qb.setProjectionMap(Messages.PROJECTION_MAP);
			if (TextUtils.isEmpty(orderBy)) {
				orderBy = Messages.DEFAULT_SORT_ORDER;
			}
			break;
		case THREADS_CACHE:
			if (TextUtils.isEmpty(orderBy)) {
				orderBy = Threads.DEFAULT_SORT_ORDER;
			}
			Log.d(TAG, "return Cursor directly");
			return db.query(Threads.TABLE, projection, selection,
					selectionArgs, null, null, orderBy);
		case THREAD_ID:
			qb.appendWhere(Threads.ID + "=" + ContentUris.parseId(uri));
		case THREADS:
			qb.setTables(Threads.TABLE);
			qb.setProjectionMap(Threads.PROJECTION_MAP);
			if (TextUtils.isEmpty(orderBy)) {
				orderBy = Threads.DEFAULT_SORT_ORDER;
			}
			break;
		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		// Run the query
		final Cursor c = qb.query(db, projection, selection, selectionArgs,
				null, null, orderBy);

		// Tell the cursor what uri to watch, so it knows when its source data
		// changes
		c.setNotificationUri(this.getContext().getContentResolver(), uri);
		Log.d(TAG, "exit query(): " + c.getCount());
		return c;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int update(final Uri uri, final ContentValues values,
			final String selection, final String[] selectionArgs) {
		Log.d(TAG, "update: " + uri + " w: " + selection + " " + values);
		final SQLiteDatabase db = this.mOpenHelper.getWritableDatabase();
		final ContentResolver cr = this.getContext().getContentResolver();
		int ret = 0;
		final int u = URI_MATCHER.match(uri);
		switch (u) {
		case MESSAGES_CACHE:
			ret = db.update(Messages.TABLE, values, selection, selectionArgs);
			break;
		case THREADS_CACHE:
			ret = db.update(Threads.TABLE, values, selection, selectionArgs);
			break;
		case MESSAGES:
			ret = db.update(Messages.TABLE, values, selection, selectionArgs);
			cr.update(Messages.ORIG_URI_SMS, values, selection, selectionArgs);
			cr.update(Messages.ORIG_URI_MMS, values, selection, selectionArgs);
			break;
		case MESSAGE_ID:
			final long mid = ContentUris.parseId(uri);
			ret = db.update(Messages.TABLE, values, DbUtils.sqlAnd(Messages.ID
					+ "=" + mid, selection), selectionArgs);
			if (mid >= 0L) {
				cr.update(ContentUris
						.withAppendedId(Messages.ORIG_URI_SMS, mid), values,
						selection, selectionArgs);
			} else {
				cr.update(ContentUris.withAppendedId(Messages.ORIG_URI_MMS, -1L
						* mid), values, selection, selectionArgs);
			}
			break;
		case THREADS:
			ret = db.update(Threads.TABLE, values, selection, selectionArgs);
			cr.update(Threads.ORIG_URI, values, selection, null);
			break;
		case MESSAGES_TID:
			long tid = ContentUris.parseId(uri);
			ret += db.update(Messages.TABLE, values, DbUtils.sqlAnd(
					Messages.THREADID + "=" + tid, selection), selectionArgs);
		case THREAD_ID:
			tid = ContentUris.parseId(uri);
			ret = db.update(Threads.TABLE, values, DbUtils.sqlAnd(Threads.ID
					+ "=" + tid, selection), selectionArgs);
			try {
				cr.update(ContentUris.withAppendedId(Threads.ORIG_URI, tid),
						values, selection, null);
			} catch (SQLiteException e) {
				Log.w(TAG, "unable to update Threads.ORIG_URI", e);
			}
			break;
		default:
			throw new IllegalArgumentException("Unknown Uri " + uri);
		}
		if (ret > 0) {
			cr.notifyChange(Messages.CONTENT_URI, null);
			if (u != MESSAGES_CACHE && u != THREADS_CACHE) {
				SyncService.syncThreads(this.getContext(), -1);
			}
		}
		Log.d(TAG, "exit update(): " + ret);
		return ret;
	}

	/**
	 * Get display name.
	 * 
	 * @param address
	 *            address
	 * @param name
	 *            name
	 * @param full
	 *            true for "name &lt;address&gt;"
	 * @return display name
	 */
	public static String getDisplayName(final String address,
			final String name, final boolean full) {
		if (name == null || name.trim().length() == 0) {
			return address;
		}
		if (full) {
			return name + " <" + address + ">";
		}
		return name;
	}

	/**
	 * Mark all messages with a given {@link Uri} as read.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param uri
	 *            {@link Uri}
	 * @param read
	 *            read status
	 */
	public static void markRead(final Context context, final Uri uri,
			final int read) {
		if (uri == null) {
			return;
		}
		final String select = Messages.SELECTION_UNREAD.replace("0", String
				.valueOf(1 - read));
		final ContentResolver cr = context.getContentResolver();
		final Cursor cursor = cr.query(uri, Messages.PROJECTION, select, null,
				null);
		if (cursor != null && cursor.getCount() <= 0) {
			if (uri.equals(Messages.CONTENT_URI)) {
				SmsReceiver.updateNewMessageNotification(context, null);
			}
			cursor.close();
			return;
		}
		if (cursor != null && !cursor.isClosed()) {
			cursor.close();
		}

		final ContentValues cv = new ContentValues();
		cv.put(Messages.READ, read);
		cr.update(uri, cv, select, null);
		SmsReceiver.updateNewMessageNotification(context, null);
	}

	/**
	 * Delete messages with a given {@link Uri}.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param uri
	 *            {@link Uri}
	 * @param title
	 *            title of Dialog
	 * @param message
	 *            message of the Dialog
	 * @param activity
	 *            {@link Activity} to finish when deleting.
	 */
	public static void deleteMessages(final Context context, final Uri uri,
			final int title, final int message, final Activity activity) {
		final ContentResolver cr = context.getContentResolver();
		final Cursor cursor = cr.query(uri, Messages.PROJECTION, null, null,
				null);
		if (cursor == null) {
			return;
		}
		final int l = cursor.getCount();
		if (cursor != null && !cursor.isClosed()) {
			cursor.close();
		}
		if (l == 0) {
			return;
		}

		final Builder builder = new Builder(context);
		builder.setTitle(title);
		builder.setMessage(message);
		builder.setNegativeButton(android.R.string.no, null);
		builder.setPositiveButton(android.R.string.yes,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog,
							final int which) {
						cr.delete(uri, null, null);
						if (activity != null) {
							activity.finish();
						}
						SmsReceiver.updateNewMessageNotification(context, null);
					}
				});
		builder.create().show();
	}
}