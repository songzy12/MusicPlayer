package com.example.musicplayer;

import java.util.ArrayList;
import java.util.HashMap;

import com.baidu.utils.LogUtil;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class PlayListActivity extends ListActivity {
	public ArrayList<HashMap<String, String>> songsList = new ArrayList<HashMap<String, String>>();
	protected static final String TAG = "PlayListActivity";
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.playlist);
		
		Bundle b = getIntent().getExtras();
		long[] mySongIds = b.getLongArray("mSongIds");
		LogUtil.i(TAG, "mSongIds: " + mySongIds.toString());
		
		for (int i = 0; i < mySongIds.length; i++) {
			// creating new HashMap
			HashMap<String, String> song = new HashMap<String, String>();
			song.put("songTitle", mySongIds[i] + "");
			// adding HashList to ArrayList
			songsList.add(song);
		}

		// Adding menuItems to ListView
		ListAdapter adapter = new SimpleAdapter(this, songsList,
				R.layout.playlist_item, new String[] { "songTitle" }, new int[] {
						R.id.songTitle });

		setListAdapter(adapter);
		
		// selecting single ListView item
		ListView lv = getListView();
		// listening to single listitem click
		lv.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				// getting listitem index
				int songIndex = position;
				
				// Starting new intent
				Intent in = new Intent(getApplicationContext(),
						MainActivity.class);
				// Sending songIndex to PlayerActivity
				in.putExtra("songIndex", songIndex);
				setResult(100, in);
				// Closing PlayListView
				finish();
			}
		});
	}
}
