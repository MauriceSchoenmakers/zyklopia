package com.zyklopia;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.zyklopia.net.RtpPacket;

public class VideoCamera extends Activity implements SurfaceHolder.Callback,
		MediaRecorder.OnErrorListener, MediaPlayer.OnErrorListener,
		OnClickListener, OnLongClickListener {

	Thread t;
	Context mContext = this;

	private static final String TAG = "videocamera";

	private static int UPDATE_RECORD_TIME = 1;

	private static final float VIDEO_ASPECT_RATIO = 176.0f / 144.0f;
	private Camera mCamera;
	private VideoPreview mVideoPreview;
	private Button mStartStreaming;
	private Button mStopStreaming;
	private SurfaceHolder mSurfaceHolder = null;
	private VideoView mVideoFrame;
	private MediaController mMediaController;
	private MediaRecorder mMediaRecorder;
	private boolean mMediaRecorderRecording = false;
	private TextView mRecordingTimeView, mFPS;
	private Handler mHandler = new MainHandler();
	private LocalSocket receiver, sender;
	private LocalServerSocket lss;
	int obuffering;
	int fps;
	long startTime;
	boolean isRecording;

	/**
	 * This Handler is used to post message back onto the main thread of the
	 * application
	 */
	private class MainHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			long now = System.currentTimeMillis(); 
			long delta = now - startTime;

			long seconds = (delta + 500) / 1000; // round to nearest
			long minutes = seconds / 60;
			long hours = minutes / 60;
			long remainderMinutes = minutes - (hours * 60);
			long remainderSeconds = seconds - (minutes * 60);

			String secondsString = Long.toString(remainderSeconds);
			if (secondsString.length() < 2) {
				secondsString = "0" + secondsString;
			}
			String minutesString = Long.toString(remainderMinutes);
			if (minutesString.length() < 2) {
				minutesString = "0" + minutesString;
			}
			String text = minutesString + ":" + secondsString;
			if (hours > 0) {
				String hoursString = Long.toString(hours);
				if (hoursString.length() < 2) {
					hoursString = "0" + hoursString;
				}
				text = hoursString + ":" + text;
			}
			if (isRecording) {
				mRecordingTimeView.setText("Recording time: "+text);
			} else {
				mRecordingTimeView.setText("");
			}
			if (fps != 0)
				mFPS.setText(fps + "fps");
			if (mVideoFrame != null) {
				int buffering = mVideoFrame.getBufferPercentage();
				if (buffering != 100 && buffering != 0) {
					mMediaController.show();
				}
				if (buffering != 0 && !mMediaRecorderRecording)
					mVideoPreview.setVisibility(View.INVISIBLE);
				if (obuffering != buffering && buffering == 100) {
						//&& rtp_socket != null) {
					// send keep alive
				}
				obuffering = buffering;
			}

			// Work around a limitation of the T-Mobile G1: The T-Mobile
			// hardware blitter can't pixel-accurately scale and clip at the
			// same time,
			// and the SurfaceFlinger doesn't attempt to work around this
			// limitation.
			// In order to avoid visual corruption we must manually refresh the
			// entire
			// surface view when changing any overlapping view's contents.
			mVideoPreview.invalidate();
			mHandler.sendEmptyMessageDelayed(UPDATE_RECORD_TIME, 1000);
		}
	};

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		// setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
		requestWindowFeature(Window.FEATURE_PROGRESS);
		setScreenOnFlag();
		setContentView(R.layout.main);

		mStartStreaming = (Button) findViewById(R.id.startStreaming);
		mStartStreaming.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				try {
					startTime = System.currentTimeMillis();
					isRecording = true;
					startVideoRecording();
				} catch (Throwable ex) {
					//eat
				}
			}
		});
		
		mStopStreaming = (Button) findViewById(R.id.stopStreaming);
		mStopStreaming.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				try {
					isRecording = false;
					stopVideoRecording();
					// restart video
					t.interrupt();
					initializeVideo();
				} catch (Throwable ex) {
					//eat
				}
			}
		});
		
		mVideoPreview = (VideoPreview) findViewById(R.id.camera_preview);
		mVideoPreview.setAspectRatio(VIDEO_ASPECT_RATIO);

		// don't set mSurfaceHolder here. We have it set ONLY within
		// surfaceCreated / surfaceDestroyed, other parts of the code
		// assume that when it is set, the surface is also set.
		SurfaceHolder holder = mVideoPreview.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		mRecordingTimeView = (TextView) findViewById(R.id.recording_time);
		mFPS = (TextView) findViewById(R.id.fps);
		mVideoFrame = (VideoView) findViewById(R.id.video_frame);
	}

	int speakermode;
	boolean justplay;

	public void onStart() {
		super.onStart();
	}

	public void onResume() {
		super.onResume();
		if (true) {
			receiver = new LocalSocket();
			try {
				lss = new LocalServerSocket("Zyklopia");
				receiver.connect(new LocalSocketAddress("Zyklopia"));
				receiver.setReceiveBufferSize(500000);
				receiver.setSendBufferSize(500000);
				sender = lss.accept();
				sender.setReceiveBufferSize(500000);
				sender.setSendBufferSize(500000);
			} catch (IOException e1) {
				e1.printStackTrace();
				finish();
				return;
			}
			mVideoPreview.setVisibility(View.VISIBLE);
			if (!mMediaRecorderRecording)
				initializeVideo();
			
		} else { /*
				 * TODO if ( Receiver.engine(mContext).getRemoteVideo() != 0 &&
				 * PreferenceManager
				 * .getDefaultSharedPreferences(this).getString(
				 * org.sipdroid.sipua.ui.Settings.PREF_SERVER,
				 * org.sipdroid.sipua
				 * .ui.Settings.DEFAULT_SERVER).equals(org.sipdroid
				 * .sipua.ui.Settings.DEFAULT_SERVER)) {
				 * mVideoFrame.setVideoURI(
				 * Uri.parse("rtsp://"+Receiver.engine(mContext
				 * ).getRemoteAddr()+"/"+
				 * Receiver.engine(mContext).getRemoteVideo()+"/sipdroid"));
				 */
			mVideoFrame
					.setMediaController(mMediaController = new MediaController(
							this));
			mVideoFrame.setOnErrorListener(this);
			mVideoFrame.requestFocus();
			mVideoFrame.start();
		}

		mRecordingTimeView.setText("");
		mRecordingTimeView.setVisibility(View.VISIBLE);
		mHandler.sendEmptyMessage(UPDATE_RECORD_TIME);
	}

	public void onPause() {
		super.onPause();

		// This is similar to what mShutterButton.performClick() does,
		// but not quite the same.
		if (mMediaRecorderRecording) {
			stopVideoRecording();

			try {
				lss.close();
				receiver.close();
				sender.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// TODO Receiver.engine(this).speaker(speakermode);
		finish();
	}

	/*
	 * catch the back and call buttons to return to the in call activity.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		switch (keyCode) {
		// finish for these events
		case KeyEvent.KEYCODE_CALL:
			// TODO Receiver.engine(this).togglehold();
		case KeyEvent.KEYCODE_BACK:
			finish();
			return true;

		case KeyEvent.KEYCODE_CAMERA:
			// Disable the CAMERA button while in-call since it's too
			// easy to press accidentally.
			return true;

		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_VOLUME_UP:
			// TODO RtpStreamReceiver.adjust(keyCode,true);
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		boolean result = super.onPrepareOptionsMenu(menu);

		// TODO if (mMediaRecorderRecording)
		// menu.findItem(VIDEO_MENU_ITEM).setVisible(false);
		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		/*
		 * TODO case VIDEO_MENU_ITEM: intent.removeExtra("justplay");
		 * onResume(); return true;
		 */
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if (!justplay && !mMediaRecorderRecording)
			initializeVideo();
	}

	public void surfaceCreated(SurfaceHolder holder) {
		mSurfaceHolder = holder;
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		mSurfaceHolder = null;
	}

	// initializeVideo() starts preview and prepare media recorder.
	// Returns false if initializeVideo fails
	private boolean initializeVideo() {
		Log.i(TAG, "initializeVideo");

		if (mSurfaceHolder == null) {
			Log.i(TAG, "SurfaceHolder is null");
			return false;
		}

		mMediaRecorderRecording = true;

		if (mMediaRecorder == null)
			mMediaRecorder = new MediaRecorder();
		else
			mMediaRecorder.reset();
		if (mCamera != null) {
			mCamera.release();
			mCamera = null;
		}
		mVideoPreview.setOnLongClickListener(this);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		mMediaRecorder.setOutputFile(sender.getFileDescriptor());

		// Use the same frame rate for both, since internally
		// if the frame rate is too large, it can cause camera to become
		// unstable. We need to fix the MediaRecorder to disable the support
		// of setting frame rate for now.
		mMediaRecorder.setVideoFrameRate(15);
		mMediaRecorder.setVideoSize(176, 144);
		mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);
		mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());

		try {
			mMediaRecorder.prepare();
			mMediaRecorder.setOnErrorListener(this);
			mMediaRecorder.start();
		} catch (IOException exception) {
			releaseMediaRecorder();
			finish();
			return false;
		}
		return true;
	}

	private void releaseMediaRecorder() {
		Log.v(TAG, "Releasing media recorder.");
		if (mMediaRecorder != null) {
			mMediaRecorder.reset();
			mMediaRecorder.release();
			mMediaRecorder = null;
		}
	}

	public void onError(MediaRecorder mr, int what, int extra) {
		if (what == MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN) {
			finish();
		}
	}

	boolean change;

	private void startVideoRecording() {
		Log.i(TAG, "starting VideoRecording");

	        (t = new Thread() {
				public void run() {
					int frame_size = 1400;
					byte[] buffer = new byte[frame_size + 14];
					buffer[12] = 4;
					RtpPacket rtp_packet = new RtpPacket(buffer, 0);
					int seqn = 0;
					int num,number = 0,src,dest,len = 0,head = 0,lasthead = 0,cnt = 0,stable = 0;
					long now,lasttime = 0;
					double avgrate = 24000;
					double avglen = avgrate/20;
					Socket rtp_socket;
					try {
						rtp_socket = new Socket(InetAddress.getByName("zyklopia.com"),9100);
					
//						 mVideoFrame.setVideoURI(Uri.parse("rtsp://"+Receiver.engine(mContext).getRemoteAddr()+"/"+
//								 * Receiver.engine(mContext).getRemoteVideo()+"/sipdroid"));
//								 */
//							mVideoFrame
//									.setMediaController(mMediaController = new MediaController(
//											this));
//							mVideoFrame.setOnErrorListener(this);
//							mVideoFrame.requestFocus();
//							mVideoFrame.start();
					
					
					} catch (Exception e) {
						Log.e(TAG,"Error opening socket: "+e);
						Looper.prepare();
						Toast.makeText(VideoCamera.this,"Could not connect to zyklopia.com\nPLZ check your network settings!",
								Toast.LENGTH_LONG).show();
						return;
					}		
					
					InputStream fis = null;
					try {
	   					fis = receiver.getInputStream();
					} catch (IOException e1) {
						Log.e(TAG,"Error opening socket: "+e1);
						try {
							rtp_socket.close();
						} catch (IOException e) {
							//eat
						}
						return;
					}
					
 					rtp_packet.setPayloadType(103);
					while (videoValid()) {
						num = -1;
						try {
							fis.read(buffer);
							rtp_socket.getOutputStream().write(buffer);
							//num  = fis.read(buffer,14+number,frame_size-number);
						} catch (IOException e) {
							Log.e(TAG,"Error opening socket: "+e);
							break;
						}
						if (num < 0) {
							try {
								sleep(20);
							} catch (InterruptedException e) {
								break;
							}
							continue;							
						}
						number += num;
						head += num;
						try {
							if (lasthead != head+fis.available() && ++stable >= 5) {
								now = SystemClock.elapsedRealtime();
								if (lasttime != 0) {
									fps = (int)((double)cnt*1000/(now-lasttime));
									avgrate = (double)fis.available()*1000/(now-lasttime);
								}
								if (cnt != 0 && len != 0)
									avglen = len/cnt;
								lasttime = now;
								lasthead = head+fis.available();
								len = cnt = stable = 0;
							}
						} catch (IOException e1) {
							Log.e(TAG,"Error: "+e1);
							break;
						}
						
    					for (num = 14; num <= 14+number-2; num++)
							if (buffer[num] == 0 && buffer[num+1] == 0) break;
						if (num > 14+number-2) {
							num = 0;
							rtp_packet.setMarker(false);
						} else {	
							num = 14+number - num;
							rtp_packet.setMarker(true);
						}
						
			 			rtp_packet.setSequenceNumber(seqn++);
			 			rtp_packet.setPayloadLength(number-num+2);
			 			if (seqn > 10) try {
			 				rtp_socket.getOutputStream().write(rtp_packet.getPacket());
    			 			len += number-num;
			 			} catch (IOException e) {
			 				Log.e(TAG,"Error writing to socket: "+e);
			 				break;
			 			}
						
			 			if (num > 0) {
				 			num -= 2;
				 			dest = 14;
				 			src = 14+number - num;
				 			if (num > 0 && buffer[src] == 0) {
				 				src++;
				 				num--;
				 			}
				 			number = num;
				 			while (num-- > 0)
				 				buffer[dest++] = buffer[src++];
							buffer[12] = 4;
							
							cnt++;
							try {
								if (avgrate != 0)
									Thread.sleep((int)(avglen/avgrate*1000));
							} catch (Exception e) {
								break;
							}
    			 			rtp_packet.setTimestamp(SystemClock.elapsedRealtime()*90);
			 			} else {
			 				number = 0;
							buffer[12] = 0;
			 			}
			 			if (change) {
			 				change = false;
			 				long time = SystemClock.elapsedRealtime();
			 				
	    					try {
								while (fis.read(buffer,14,frame_size) > 0 &&
										SystemClock.elapsedRealtime()-time < 3000);
							} catch (Exception e) {
							}
			 				number = 0;
							buffer[12] = 0;
			 			}
					}
					try {
						rtp_socket.close();
					} catch (IOException e) {
						//eat
					}
					try {
						while (fis.read(buffer,0,frame_size) > 0);
					} catch (IOException e) {
						//eat
					}
				}
			}).start();   
        
	}

	private void stopVideoRecording() {
		Log.v(TAG, "stopVideoRecording");
		if (t != null && (mMediaRecorderRecording || mMediaRecorder != null)) {
			// TODO Receiver.listener_video = null;
			t.interrupt();
			// TODO RtpStreamSender.delay = 0;

			if (mMediaRecorderRecording && mMediaRecorder != null) {
				try {
					mMediaRecorder.setOnErrorListener(null);
					mMediaRecorder.setOnInfoListener(null);
					mMediaRecorder.stop();
				} catch (RuntimeException e) {
					Log.e(TAG, "stop fail: " + e.getMessage());
				}

				mMediaRecorderRecording = false;
			}
			releaseMediaRecorder();
		}
	}

	private void setScreenOnFlag() {
		Window w = getWindow();
		final int keepScreenOnFlag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
		if ((w.getAttributes().flags & keepScreenOnFlag) == 0) {
			w.addFlags(keepScreenOnFlag);
		}
	}

	public void onHangup() {
		finish();
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_VOLUME_UP:
			// TODO RtpStreamReceiver.adjust(keyCode,false);
			return true;
		case KeyEvent.KEYCODE_ENDCALL:
			/*
			 * TODO if (Receiver.pstn_state == null ||
			 * (Receiver.pstn_state.equals("IDLE") &&
			 * (SystemClock.elapsedRealtime()-Receiver.pstn_time) > 3000)) {
			 * Receiver.engine(mContext).rejectcall(); return true; }
			 */
			break;
		}
		return false;
	}

	static TelephonyManager tm;

	static boolean videoValid() {
		/*
		 * TODO if (Receiver.on_wlan) return true;
		 * 
		 * if (tm == null) tm = (TelephonyManager)
		 * Receiver.mContext.getSystemService(Context.TELEPHONY_SERVICE); if
		 * (tm.getNetworkType() < TelephonyManager.NETWORK_TYPE_UMTS) return
		 * false;
		 */
		return true;
	}

	public boolean onError(MediaPlayer mp, int what, int extra) {
		return true;
	}

	public void onClick(View v) {
		initializeVideo();
		change = true;
	}

	public boolean onLongClick(View v) {
		initializeVideo();
		change = true;
		return true;
	}

}
