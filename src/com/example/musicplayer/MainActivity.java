package com.example.musicplayer;

import java.io.File;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.music.SDKEngine;
import com.baidu.music.SDKInterface;
import com.baidu.music.interfaces.OAuthInterface;
import com.baidu.music.manager.ImageManager;
import com.baidu.music.manager.OAuthManager;
import com.baidu.music.model.LrcPic;
import com.baidu.music.model.LrcPicList;
import com.baidu.music.onlinedata.OnlineManagerEngine;
import com.baidu.music.onlinedata.PlayinglistManager;
import com.baidu.music.onlinedata.SearchManager.LrcPicSearchListener;
import com.baidu.music.player.StreamPlayer;
import com.baidu.music.player.StreamPlayer.OnBlockListener;
import com.baidu.music.player.StreamPlayer.OnBufferingUpdateListener;
import com.baidu.music.player.StreamPlayer.OnCompletionListener;
import com.baidu.music.player.StreamPlayer.OnPreparedListener;
import com.baidu.music.player.StreamPlayer.OnShowLinkListener;
import com.baidu.utils.LogUtil;
import com.baidu.utils.TextUtil;

/**
 * 鎾斁鍦ㄧ嚎闊充箰Demo
 * 
 * @version 2012-9-29
 */
public class MainActivity extends Activity implements SDKInterface {
    private final static String APPKEY = "H7THeqTNzDcUOpNuvs0gXGIL";
    private final static String SECRETKEY = "KCzK1bvf7yAmzNbMsPTwcw7uNaqmGBox";
    private final static String SCOPE = "music_media_basic";

    private AudioManager mAudioManager;
    private OAuthManager manager;
    private SDKEngine engine;
    
    private static final int REFRESH_POSITION = 1;
    private static final int REFRESH_DURATION = 2;
    private StreamPlayer mStreamPlayer;

    private static final int STOPPED = 1;
    private static final int PLAYING = 2;
    private static final int PAUSED = 3;
    protected static final String TAG = "StreamPlayDemo";
    
    private long mSongId = 0;
    private String mBit;    

    private ImageButton mBtnPlay;
    private ImageButton mBtnNext;
    private ImageButton mBtnPrevious;
	private ImageButton mBtnPlaylist;
	private ImageButton mBtnRepeat;
	private ImageButton mBtnShuffle;
    private SeekBar mSongProgressBar;
    private TextView mSongCurrentDurationLabel;
    private TextView mSongTotalDurationLabel;
    private TextView mSongTitle;    
    private ImageView mSongThumbnail;
    private int seekForwardTime = 5000; // 5000 milliseconds
	private int seekBackwardTime = 5000; // 5000 milliseconds
	private boolean isShuffle = false;
	private boolean isRepeat = false;

	private NotificationManager mNotificationManager;
	private NotificationCompat.Builder mBuilder;
	private int notifyId = 100;
	
    
    private GestureDetector mDetector;

    private long[] mSongIds = new long[] { 3454712, 31655244, 8061609, 114626026, 123772450, 130244125, 38280826, 7348261};
    private int currentSongIndex = 0;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH_POSITION:
                    long position = mStreamPlayer.position();
                    mSongCurrentDurationLabel.setText(strftime(Math.max(position, 0)));
                    mSongProgressBar.setProgress((int) mStreamPlayer.position());
                    break;
                case REFRESH_DURATION:
                    mSongTotalDurationLabel.setText(strftime(mStreamPlayer.duration()));
                    setMusicInfo();
                    break;
                default:
                    break;
            }
        }
    };
    
    Timer mTimer;
    private int status = STOPPED;

    OnPreparedListener mOnPreparedListener = new OnPreparedListener() {

        @Override
        public void onPrepared() {
        	if (mSongProgressBar != null) {
                mSongProgressBar.setMax((int) mStreamPlayer.duration());
                mHandler.sendEmptyMessage(REFRESH_DURATION);
                mHandler.sendEmptyMessage(REFRESH_POSITION);
            }
            LogUtil.d(TAG, "onPrepared....");
            // if (status == PLAYING && !mStreamPlayer.isPlaying())
            mStreamPlayer.start();
        }
    };

    OnBufferingUpdateListener mOnBufferingUpdateListener = new OnBufferingUpdateListener() {
        @Override
        public void onBufferingUpdate(int percent) {
            LogUtil.d(TAG, "onBufferingUpdate" + percent);
        }

        @Override
        public void onBufferingEnd() {
            LogUtil.d(TAG, "onBufferingEnd");
        }
    };

    OnCompletionListener mOnCompletionListener = new OnCompletionListener() {
        @Override
        public void onCompletion() {
            LogUtil.d(TAG, "completed.");
            next();
        }
    };
    
    OnBlockListener mOnBlockListener = new OnBlockListener() {

        @Override
        public void onBlocked() {
            LogUtil.d(TAG,
                    "onBlocked... isPlaying : " + mStreamPlayer.isPlaying());
            mStreamPlayer.pause();
            status = PAUSED;
            checkNetConnected();
        }
    };

    OnShowLinkListener mOnShowLinkListener = new OnShowLinkListener() {
        @Override
        public void onGetShowLink(String url, String musicId) {
            LogUtil.d(TAG, "musicId:" + musicId + "onGetShowLink : " + url);
        }

        @Override
        public void onIsShowLink(boolean isShowLink, String musicId) {
            LogUtil.d(TAG, "musicId:" + musicId + "onIsShowLink : " + isShowLink);
        }
    };

    GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {
    	float MIN_MOVE = 100;
    	
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float vX, float vY) {
        	LogUtil.d(TAG, "onScroll...");
            return false;
        }
        
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
        	LogUtil.d(TAG, "onFling...");
        	float deltaX = e2.getX() - e1.getX(); 
            if(deltaX > MIN_MOVE){
                fastForward(deltaX);
            } else if(deltaX < -MIN_MOVE){
                rewind(-deltaX);
            } 
            
            float deltaY = e2.getY() - e1.getY(); 
            if(deltaY > MIN_MOVE){
                volumeDown(deltaY);
            } else if(deltaY < -MIN_MOVE){
                volumeUp(-deltaY);
            }
            return false;
        }
    };
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mDetector.onTouchEvent(event);
    }
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtil.setDebugMode(true);
        LogUtil.d(TAG, "onCreate...");
        setContentView(R.layout.player);
        
        engine = SDKEngine.getInstance();
        engine.init(getApplicationContext(), APPKEY, SECRETKEY, SCOPE, this);

        manager = OAuthManager.getInstance(getApplicationContext());
   	 	mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
   	 	mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
   	 	File sdcardPath = Environment.getExternalStorageDirectory();
   	 	// sdcard_path.toString() == "/storage/emulated/0";
   	 	String folder = "baidu/music";
   	 	File dirPath = new File(sdcardPath + "/" + folder + "/");
		if (!dirPath.exists())	
			dirPath.mkdirs();
   	 	ImageManager.init(this, ImageManager.POSTFIX_JPG,
   	 		dirPath.toString(), 1 * 1024 * 1024);
   	 
        /**
         * 闁村瓨娼�
         */
        if (manager.validate() < 5 * 24 * 60 * 60) {
            manager.authorize(new OAuthInterface.onAuthorizeFinishListener() {

                @Override
                public void onAuthorizeFinish(int status) {
                    LogUtil.d(TAG, " onAuthorizeFinish status = " + status);
                }
            });
        }
        PlayinglistManager.getInstance(getApplicationContext()).initPlayer(getApplicationContext());
        
        mSongId = getIntent().getLongExtra("song_id", 0);
        mBit = getIntent().getStringExtra("bitrate");
        mBtnPlay = (ImageButton) findViewById(R.id.btnPlay);
        mBtnNext = (ImageButton) findViewById(R.id.btnNext);
        mBtnPrevious = (ImageButton) findViewById(R.id.btnPrevious);
        mBtnPlaylist = (ImageButton) findViewById(R.id.btnPlaylist);
        mBtnRepeat = (ImageButton) findViewById(R.id.btnRepeat);
		mBtnShuffle = (ImageButton) findViewById(R.id.btnShuffle);
        mSongProgressBar = (SeekBar) findViewById(R.id.songProgressBar);
        mSongCurrentDurationLabel = (TextView) findViewById(R.id.songCurrentDurationLabel);
        mSongTotalDurationLabel = (TextView) findViewById(R.id.songTotalDurationLabel);
        mSongTitle = (TextView) findViewById(R.id.songTitle);
        mSongThumbnail = (ImageView) findViewById(R.id.adele);
        
        mDetector = new GestureDetector(mSongThumbnail.getContext(), mGestureListener);
        
        mBtnPlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	LogUtil.i(TAG, "status: " + status);
                switch (status) {
                	case PLAYING: pause(); break;
                	case PAUSED: resume(); break;
                	default: play(mSongId); break;
                 }
            }
        });
        
        mBtnNext.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                next();
            }
        });
        
        mBtnPrevious.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                previous();
            }
        });
        
        mBtnRepeat.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				if(isRepeat){
					isRepeat = false;
					mBtnRepeat.setImageResource(R.drawable.btn_repeat);
				}else{
					// make repeat to true
					isRepeat = true;
					// make shuffle to false
					isShuffle = false;
					mBtnRepeat.setImageResource(R.drawable.btn_repeat_focused);
					mBtnShuffle.setImageResource(R.drawable.btn_shuffle);
				}	
			}
		});
        
        mBtnShuffle.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				if(isShuffle){
					isShuffle = false;
					mBtnShuffle.setImageResource(R.drawable.btn_shuffle);
				}else{
					// make repeat to true
					isShuffle= true;
					// make shuffle to false
					isRepeat = false;
					mBtnShuffle.setImageResource(R.drawable.btn_shuffle_focused);
					mBtnRepeat.setImageResource(R.drawable.btn_repeat);
				}	
			}
		});
        
		mBtnPlaylist.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Bundle b = new Bundle();
				b.putLongArray("mSongIds", mSongIds);
				Intent i = new Intent(getApplicationContext(), PlayListActivity.class);
				i.putExtras(b);
				startActivityForResult(i, 100);			
			}
		});

        mSongProgressBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                int pos = (int) mStreamPlayer.seek(progress);
                seekBar.setProgress(pos);
                mSongCurrentDurationLabel.setText(strftime(mStreamPlayer.position()));
            }
        });
        
        IntentFilter filter = new IntentFilter();  
        filter.addAction(Intent.ACTION_HEADSET_PLUG);  
        registerReceiver(headsetReceiver, filter); 
        
        IntentFilter phoneFilter = new IntentFilter("android.intent.action.PHONE_STATE");  
        phoneFilter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        registerReceiver(phoneReceiver, phoneFilter);

        mStreamPlayer = new StreamPlayer(this);
        mStreamPlayer.setOnPreparedListener(mOnPreparedListener);
        mStreamPlayer.setOnBufferingUpdateListener(mOnBufferingUpdateListener);
        mStreamPlayer.setOnCompletionListener(mOnCompletionListener);
        mStreamPlayer.setOnBlockListener(mOnBlockListener);
        mStreamPlayer.setOnShowLinkListener(new OnShowLinkListener() {

            @Override
            public void onIsShowLink(boolean isShowLink, String musicId) {
                LogUtil.d(TAG, "musicId:" + musicId + "onIsShowLink : "
                        + isShowLink);
            }

            @Override
            public void onGetShowLink(String url, String musicId) {
                LogUtil.d(TAG, "musicId:" + musicId + "onGetShowLink : " + url);
            }
        });
        
        if (mSongId == 0)
        	mSongId = mSongIds[currentSongIndex];
        LogUtil.i(TAG, "status(1 for stop, 2 for play, 3 for pause): " + status);
    }
    
    @Override
    protected void onActivityResult(int requestCode,
                                     int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == 100){
        	currentSongIndex = data.getExtras().getInt("songIndex");
        	mSongId = mSongIds[currentSongIndex];
            play(mSongId);
        }
    }
    
    private BroadcastReceiver headsetReceiver = new BroadcastReceiver() {  
        @Override  
        public void onReceive(Context context, Intent intent) {    
             LogUtil.i(TAG, "headset state(0 for resume, 1 for pause): "+intent.getIntExtra("state", 0));
             if(intent.getIntExtra("state", 0) == 1){  
            	 if (status == PAUSED)
            		 resume();
             }else if(intent.getIntExtra("state", 0) == 0){  
            	 if (status == PLAYING)
            		 pause();  
             }
        }  
    };  
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mStreamPlayer != null) {
            mStreamPlayer.reset();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }
    
    public void onBackPressed(){
    	new AlertDialog.Builder(this).setTitle("Run in background?")
        .setIcon(android.R.drawable.ic_dialog_info)
        .setPositiveButton("No", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 点击“确认”后的操作
                MainActivity.this.finish();
            }
        })
        .setNegativeButton("Yes", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                bg();
                notification();
            }
        }).show();
    }
    
    private void bg() {
    	PackageManager pm = getPackageManager();
    	ResolveInfo homeInfo = pm.resolveActivity(new Intent(Intent.ACTION_MAIN)
    	.addCategory(Intent.CATEGORY_HOME), 0);
    	ActivityInfo ai = homeInfo.activityInfo;
        Intent startIntent = new Intent(Intent.ACTION_MAIN);
        startIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        startIntent.setComponent(new ComponentName(ai.packageName, ai.name));
        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(startIntent);
        } catch (SecurityException e) {
            LogUtil.e(TAG, "Make sure to create a MAIN intent-filter for the corresponding activity.");
        }
    }
    
    public void notification(){
    	mBuilder = new NotificationCompat.Builder(this);
		mBuilder.setAutoCancel(true)//点击后让通知将消失  
				.setOngoing(true)//ture，设置他为一个正在进行的通知。他们通常是用来表示一个后台任务,用户积极参与(如播放音乐)或以某种方式正在等待,因此占用设备(如一个文件下载,同步操作,主动网络连接)
				.setWhen(System.currentTimeMillis())//通知产生的时间，会在通知信息里显示
				.setPriority(Notification.PRIORITY_DEFAULT)//设置该通知优先级
				.setDefaults(Notification.DEFAULT_SOUND)//向通知添加声音、闪灯和振动效果的最简单、最一致的方式是使用当前的用户默认设置，使用defaults属性，可以组合：
				.setContentTitle("Music Player")
				.setContentText("Click to return")
				.setTicker("Click me")
				.setSmallIcon(R.drawable.ic_launcher);
		//点击的意图ACTION是跳转到Intent
		Intent resultIntent = new Intent(this, MainActivity.class);
		resultIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(pendingIntent);
		mNotificationManager.notify(notifyId, mBuilder.build());
	}
    
    private void setMusicInfo(){
    	long musicId = mStreamPlayer.getMusicId();
    	LogUtil.i(TAG, "musicId: "+musicId);

    	String artist = mStreamPlayer.getMusicArtist();
    	String title = mStreamPlayer.getMusicTitle();
    	String album = mStreamPlayer.getMusicAlbum();
    	mSongTitle.setText(title+"-"+artist);
    	loadImage(title, artist);
    }
    
    private void loadImage(String title, String artist) {
    	LogUtil.i(TAG, "title: " + title + ", artist: " + artist);
        OnlineManagerEngine
                .getInstance(getApplicationContext())
                .getSearchManager(getBaseContext())
                .getLyricPicAsync(getApplicationContext(),
                        title, artist,
                        new LrcPicSearchListener() {
                            @Override
                            public void onGetLrcPicList(LrcPicList list) {
                            	if (list.getErrorCode() == LrcPicList.OK) {
                                    LrcPic lrcPic = (LrcPic) list.getItems()
                                            .get(0);
                                    LogUtil.i(TAG, "LrcPic: " + lrcPic.toString());
                                    ImageManager.render(lrcPic.getPicBig(),
                                            mSongThumbnail, -1, -1, 0, false, false);
                                }
                            }
                        });
    }
    
    private void play(long mSongId) {
    	checkNetConnected();
        mStreamPlayer.reset();
    	LogUtil.i(TAG, "currentSongIndex: " + currentSongIndex + ", mSongId: " + mSongId);
	    mStreamPlayer.prepare(mSongId, TextUtil.isEmpty(mBit) ? "128" : mBit);
        status = PLAYING;
        mBtnPlay.setImageResource(R.drawable.btn_pause);
        setTimer();
    }

    private void setTimer() {
    	if(mTimer != null)
    		return;
        mTimer = new Timer(true);
        TimerTask task = new TimerTask() {
            public void run() {
                mHandler.sendEmptyMessage(REFRESH_POSITION);
            }
        };
        mTimer.scheduleAtFixedRate(task, 0, 1000);    
    }
    
    private String strftime(long millisecond) {
    	long second = millisecond / 1000;
    	long minute = second / 60;
    	String time = String.format(Locale.getDefault(), "%02d:%02d", minute, second % 60);
    	LogUtil.d(TAG, "millisecond: " + millisecond + ", time: " + time);
    	return time;
    }

    public void resume() {
    	checkNetConnected();
    	mStreamPlayer.start();
        status = PLAYING;
        mBtnPlay.setImageResource(R.drawable.btn_pause);
    }

    public void pause() {
        mStreamPlayer.pause();
        status = PAUSED;
        mBtnPlay.setImageResource(R.drawable.btn_play);
    }

    private void next() {
    	if(isRepeat){
			// repeat is on play same song again
		} else if(isShuffle){
			// shuffle is on - play a random song
			Random rand = new Random();
			currentSongIndex = rand.nextInt((mSongIds.length - 1) - 0 + 1) + 0;
		} else{
			// no repeat or shuffle ON - play next song
			currentSongIndex = (currentSongIndex + 1) % mSongIds.length;
		}
    	mSongId = mSongIds[currentSongIndex];
    	play(mSongId);
    }
    
    private void previous() {
        currentSongIndex = (mSongIds.length + currentSongIndex - 1) %  mSongIds.length;
        mSongId = mSongIds[currentSongIndex];
    	play(mSongId);
    }
    
    private boolean isNetConnected() {  
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);  
        if (cm != null) {  
            NetworkInfo[] infos = cm.getAllNetworkInfo();  
            if (infos != null) {  
                for (NetworkInfo ni : infos) {  
                    if (ni.isConnected()) {  
                        return true;  
                    }  
                }  
            }  
        }  
        return false;  
    } 
    
    private void checkNetConnected() {
    	if (isNetConnected())
    		return;
		LogUtil.d(TAG, "isNetConnected: " + isNetConnected());
		Toast.makeText(getApplicationContext(), "Please check your network setting...",
				Toast.LENGTH_LONG).show();
    		
    } 

    private void fastForward(float delta){
    	LogUtil.d(TAG, "fastForward: " + delta);
    	// delta typically 100, 100ms * 100 = 10000ms = 10s 
    	int progress =  Math.min(mSongProgressBar.getMax(), mSongProgressBar.getProgress() + seekForwardTime);
        mSongProgressBar.setProgress(progress);
        mSongCurrentDurationLabel.setText(strftime(mStreamPlayer.position()));
    	mStreamPlayer.seek(progress);
    }
    
    private void rewind(float delta){
    	LogUtil.d(TAG, "rewind: " + delta);
    	int progress =  Math.max(0, mSongProgressBar.getProgress() - seekBackwardTime);
        mSongProgressBar.setProgress(progress);
        mSongCurrentDurationLabel.setText(strftime(progress));
    	mStreamPlayer.seek(progress);
    }
    
    private void volumeUp(float delta){
        int max = mAudioManager.getStreamMaxVolume( AudioManager.STREAM_MUSIC );
        int current = mAudioManager.getStreamVolume( AudioManager.STREAM_MUSIC );
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.min(current + 1, max), AudioManager.FLAG_SHOW_UI);
        LogUtil.d(TAG, "max : " + max + " current : " + current);
    }
    
	private void volumeDown(float delta){
        int current = mAudioManager.getStreamVolume( AudioManager.STREAM_MUSIC );
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, current - 1), AudioManager.FLAG_SHOW_UI);
        LogUtil.d(TAG, "min : " + 0 + " current : " + current);
	}

	@Override
	public void onAccountTokenInvalid() {
		
	}

	@Override
	public void onOrdinaryInvalid() {
		
	}
	
	private BroadcastReceiver phoneReceiver = new BroadcastReceiver(){
		@Override
	    public void onReceive(Context context, Intent intent) {
	        //鎷ㄦ墦鐢佃瘽
	        if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
	            final String phoneNum = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
	            LogUtil.d(TAG, "phoneNum: " + phoneNum);
	            pause();
	        } else {
	            TelephonyManager tm = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
	            tm.listen(listener,PhoneStateListener.LISTEN_CALL_STATE);
	        }
	    }

	    PhoneStateListener listener = new PhoneStateListener(){
	        @Override
	        public void onCallStateChanged(int state, String incomingNumber) {
	            super.onCallStateChanged(state, incomingNumber);
	            switch(state){
	                //鐢佃瘽绛夊緟鎺ュ惉
	                case TelephonyManager.CALL_STATE_RINGING:
						if(status == PLAYING)
							pause();
	                    LogUtil.d(TAG, "call state ringing...");
	                    break;
	                //鐢佃瘽鎺ュ惉
	                case TelephonyManager.CALL_STATE_OFFHOOK:
                        LogUtil.d(TAG, "call state offhook...");
	                    break;
	                //鐢佃瘽鎸傛満
	                case TelephonyManager.CALL_STATE_IDLE:
                        LogUtil.d(TAG, "call state idle...");
						if (status == PAUSED)
							resume();
	                    break;
	            }
	        }
	    };
	};
}