package mmn.esmike.videoencoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import android.app.Service;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.IBinder;
import android.util.Log;

public class VideoEncoder extends Service{
	private static final String Tag          = "Encode Service";
	private static final String Raw_RGB_File = "/sdcard/fb_data";
	private static final String Mpeg4_File   = "/sdcard/test.264";
	private static final int pixel_x = 1280 , pixel_y = 800;
	private static final int RGB_x   = 800  , RGB_y   = 1280;
	private static Integer state = 0,encode=0;
	private MediaCodec codec;
	private MediaFormat format;
	private FileInputStream fileInputStream;
	private FileOutputStream fileOutputStream;
	private byte[] rgb_buffer       = new byte[pixel_x*pixel_y*4];
	private byte[] rgb_scale_buffer = new byte[(pixel_x/2)*(pixel_y/2)*3];
	private byte[] yuv_buffer       = new byte[(pixel_x/2)*(pixel_y/2)*3/2];
	private MainThread mainthread;
	private Process process;

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		InitEncoder();
		try {
			//Frame Buffer catching process
			process = Runtime.getRuntime().exec("su -c framebuffer");
			//RGB Raw data input
			fileInputStream  = new FileInputStream(new File(Raw_RGB_File));
			//Encoded data output
			fileOutputStream = new FileOutputStream(new File(Mpeg4_File));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		mainthread = new MainThread();
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		mainthread.start();
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public class Scale extends Thread{
		int src_addr_start = 0;
		int src_addr_end   = 0;
		int tar_addr_start = 0;
		public Scale(int s_start,int s_end,int t_start) {
			// TODO Auto-generated constructor stub
			src_addr_start = s_start;
			src_addr_end   = s_end;
			tar_addr_start = t_start;
			start();
		}
		@Override
		public void run() {
			// TODO Auto-generated method stub
			while(true){
				synchronized(this){
					try {
						wait();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				for(int src_pixel=src_addr_start,tar_pixel=tar_addr_start;src_pixel<src_addr_end;src_pixel+=4*2,tar_pixel+=3){
					int rotate = rotate_90_degree(tar_pixel);
					rgb_scale_buffer[rotate  ] = rgb_buffer[src_pixel  ];
					rgb_scale_buffer[rotate+1] = rgb_buffer[src_pixel+1];
					rgb_scale_buffer[rotate+2] = rgb_buffer[src_pixel+2];
					if(src_pixel%(RGB_x*4)==0){
						src_pixel+=RGB_x*4;
					}
				}
				//Scale Stage Wait Section
				synchronized(state){
					state++;
				}
			}
		}
		//Scale Stage Wake up Function
		public void wake_up(){
			synchronized(this){
				notifyAll();
			}
		}
	}
	
	public class RGB2YUV extends Thread{
		int yuv=0;
		public RGB2YUV(int type) {
			// TODO Auto-generated constructor stub
			//RGB2YUV Stage Thread selecter
			yuv=type;
			start();
		}
		@Override
		public void run() {
			// TODO Auto-generated method stub
			while(true){
				synchronized(this){
					try {
						wait();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				switch(yuv){
				case 1://RGB2YUV Stage Thread 1
					rgb2yuv420p_y(rgb_scale_buffer,yuv_buffer,pixel_x/2,pixel_y/2);
					break;
				case 2://RGB2YUV Stage Thread 1
					rgb2yuv420p_u(rgb_scale_buffer,yuv_buffer,pixel_x/2,pixel_y/2);
					break;
				case 3://RGB2YUV Stage Thread 1
					rgb2yuv420p_v(rgb_scale_buffer,yuv_buffer,pixel_x/2,pixel_y/2);
					break;
				}
				//RGB2YUV Stage Wait Section
				synchronized(state){
					state++;
				}
			}
		}
		//RGB2YUV Stage Wake up Function
		public void wake_up(){
			synchronized(this){
				notifyAll();
			}
		}
	}
	
	public class Encode extends Thread{
		public Encode() {
			// TODO Auto-generated constructor stub
			start();
		}
		@Override
		public void run() {
			// TODO Auto-generated method stub
			while(true){
				synchronized(this){
					try {
						wait();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
				//Sending buffer into openMAX encoder
				offerEncoder(yuv_buffer,yuv_buffer.length,fileOutputStream);
				
				//Encode Stage Wait Section
				synchronized(state){
					state++;
				}
			}
		}
		//Encode Stage Wake up Function
		public void wake_up(){
			synchronized(this){
				notifyAll();
			}
		}
	}

	//First Stage including : Scale Thread 1,2 & Encode Thread 1 (Total thread count : 3)
	public class Stage1{
		Scale scale1,scale2;
		Encode encode;
		public Stage1() {
			// TODO Auto-generated constructor stub
			scale1 = new Scale(0,rgb_buffer.length/2,0);
			scale2 = new Scale(rgb_buffer.length/2,rgb_buffer.length,rgb_scale_buffer.length/2);
			encode = new Encode();
		}
		public void start(boolean first){
			if(first){
				scale1.wake_up();
				scale2.wake_up();
				while(state!=2)
					;
				state=0;
				
			}
			else{
				scale1.wake_up();
				scale2.wake_up();
				encode.wake_up();
				while(state!=3){
					try {
						Thread.sleep(15);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
				state=0;
			}
		}
	}
	
	//Second Stage including : RGB to YUV Thread 1,2,3 (Total thread count : 3)
	public class Stage2{
		RGB2YUV rgb2y,rgb2u,rgb2v;
		public Stage2() {
			// TODO Auto-generated constructor stub
			rgb2y = new RGB2YUV(1);
			rgb2u = new RGB2YUV(2);
			rgb2v = new RGB2YUV(3);
		}
		public void start(){
			rgb2y.wake_up();
			rgb2u.wake_up();
			rgb2v.wake_up();
			while(state!=3){
				try {
					Thread.sleep(20);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			state=0;
		}
	}
	
	public class MainThread extends Thread{
		Stage1 stage1;
		Stage2 stage2;
		int read_len=0;
		double frame_count=0;
		long Tstart=0,Tstop=0;
		public MainThread() {
			// TODO Auto-generated constructor stub
			stage1 = new Stage1();
			stage2 = new Stage2();
		}
		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				Thread.sleep(300);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			Log.d(Tag,"Strat Processing");
			stage1.start(true);
			while(true){
				Tstart = System.currentTimeMillis();
				try {
					read_len = fileInputStream.read(rgb_buffer,0,rgb_buffer.length);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if(read_len<=0){
					Log.d(Tag,"End of File");
				}
				else{
					stage1.start(false);
					stage2.start();
					//stage3.start();
					frame_count++;
					if(frame_count%25==0){
						//Counting FPS & output to logd
						Tstop  = System.currentTimeMillis();
						Log.d(Tag,"Fps:"+25.0/(Tstop-Tstart)*100.0);
						//frame_count=0;
						//if(frame_count==600)
							//break;
						Tstart = System.currentTimeMillis();
					}
				}
			}
			/*
			Log.d(Tag,"Stop Processing");
			codec.stop();
			codec.release();
			super.run();
			*/
		}
	}


	public void InitEncoder(){
		//codec = MediaCodec.createEncoderByType("video/avc");
		//Select Nvidia Tegra H264 Codec
		codec = MediaCodec.createByCodecName("OMX.Nvidia.h264.encoder");
		format = MediaFormat.createVideoFormat("video/avc", pixel_x/2, pixel_y/2);
		format.setInteger(MediaFormat.KEY_BIT_RATE, 350000);
		format.setInteger(MediaFormat.KEY_FRAME_RATE, 20);
		format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
		format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
		format.setInteger("stride", (pixel_x/2 + 15) / 16 * 16);
		format.setInteger("slice-height", (pixel_y/2 + 15) / 16 * 16);
		codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		codec.start();
	}
	
	public int rotate_90_degree(int position){
		int pixel = position/3;
		int new_x = pixel/400+1;
		int new_y = 401 - (pixel%400+1);
		int return_val = new_x-1+(new_y-1)*640;
		return return_val*3;
	}
	
	//Push buffer into Codec queue
	public void offerEncoder(byte[] input, int input_len,FileOutputStream outputStream) {
	    try {
	        ByteBuffer[] inputBuffers = codec.getInputBuffers();
	        ByteBuffer[] outputBuffers = codec.getOutputBuffers();
	        int inputBufferIndex = codec.dequeueInputBuffer(-1);
	        if (inputBufferIndex >= 0) {
	            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
	            inputBuffer.clear();
	            inputBuffer.put(input,0,input_len);
	            codec.queueInputBuffer(inputBufferIndex, 0, input_len, 0, 0);
	        }

	        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
	        int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo,0);
	        if (outputBufferIndex >= 0) {
	            ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
	            outputBuffer.position(bufferInfo.offset);
	            outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
	            Log.d(Tag, "BufferInfo->> Offset:"+bufferInfo.offset+" Size:" + bufferInfo.size);
	            byte[] outData = new byte[bufferInfo.size];
	            outputBuffer.get(outData);
	            outputStream.write(outData, 0, outData.length);
	            //Log.i("AvcEncoder", outData.length + " bytes written");

	            codec.releaseOutputBuffer(outputBufferIndex, false);
	            //outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0);
	        }
	        
	    } catch (Throwable t) {
	        t.printStackTrace();
	    }

	}
	
	public void rgb2yuv420p_y(byte rgb[],byte yuv420p[],int width,int height){
		int r=0,g=0,b=0;
		int y=0,u=0,v=0;
		int image_size = width * height;
		for (int i = 0; i < image_size; ++i) {
			r = rgb[3 * i]&0xff;
			g = rgb[3 * i + 1]&0xff;
			b = rgb[3 * i + 2]&0xff;
			y =  i;
			yuv420p[y] = (byte) (((lookup66[r] + lookup129[g] + lookup25[b]) >> 8) + 16);
		}
	}
	public void rgb2yuv420p_u(byte rgb[],byte yuv420p[],int width,int height){
		int r=0,g=0,b=0;
		int y=0,u=0,v=0;
		int image_size = width * height;
		int upos = image_size;
		for (int i = 0; i < image_size; i+=2) {
			r = rgb[3 * i]&0xff;
			g = rgb[3 * i + 1]&0xff;
			b = rgb[3 * i + 2]&0xff;
			if (((i / width) % 2==0) && (i % 2==0)) {
				u = upos++;
				yuv420p[u] = (byte) (((lookup_m_38[r] + lookup_m_74[g] + lookup112[b]) >> 8) + 128);
			}
		}
	}
	public void rgb2yuv420p_v(byte rgb[],byte yuv420p[],int width,int height){
		int r=0,g=0,b=0;
		int y=0,u=0,v=0;
		int image_size = width * height;
		int upos = image_size;
		int vpos = upos + upos / 4;
		for (int i = 0; i < image_size; i+=2) {
			r = rgb[3 * i]&0xff;
			g = rgb[3 * i + 1]&0xff;
			b = rgb[3 * i + 2]&0xff;
			if (((i / width) % 2==0) && (i % 2==0)) {
				v = vpos++;
				yuv420p[v] = (byte) (((lookup112[r] + lookup_m_94[g] + lookup_m_18[b]) >> 8) + 128);
			}
		}
	}

/* ---RGB to YUV transform lookup table--- */
	int lookup_m_94[] = {
		      0,    -94,   -188,   -282,   -376,   -470,   -564,   -658,
		    -752,   -846,   -940,  -1034,  -1128,  -1222,  -1316,  -1410,
		  -1504,  -1598,  -1692,  -1786,  -1880,  -1974,  -2068,  -2162,
		  -2256,  -2350,  -2444,  -2538,  -2632,  -2726,  -2820,  -2914,
		  -3008,  -3102,  -3196,  -3290,  -3384,  -3478,  -3572,  -3666,
		  -3760,  -3854,  -3948,  -4042,  -4136,  -4230,  -4324,  -4418,
		  -4512,  -4606,  -4700,  -4794,  -4888,  -4982,  -5076,  -5170,
		  -5264,  -5358,  -5452,  -5546,  -5640,  -5734,  -5828,  -5922,
		  -6016,  -6110,  -6204,  -6298,  -6392,  -6486,  -6580,  -6674,
		  -6768,  -6862,  -6956,  -7050,  -7144,  -7238,  -7332,  -7426,
		  -7520,  -7614,  -7708,  -7802,  -7896,  -7990,  -8084,  -8178,
		  -8272,  -8366,  -8460,  -8554,  -8648,  -8742,  -8836,  -8930,
		  -9024,  -9118,  -9212,  -9306,  -9400,  -9494,  -9588,  -9682,
		  -9776,  -9870,  -9964, -10058, -10152, -10246, -10340, -10434,
		  -10528, -10622, -10716, -10810, -10904, -10998, -11092, -11186,
		  -11280, -11374, -11468, -11562, -11656, -11750, -11844, -11938,
		  -12032, -12126, -12220, -12314, -12408, -12502, -12596, -12690,
		  -12784, -12878, -12972, -13066, -13160, -13254, -13348, -13442,
		  -13536, -13630, -13724, -13818, -13912, -14006, -14100, -14194,
		  -14288, -14382, -14476, -14570, -14664, -14758, -14852, -14946,
		  -15040, -15134, -15228, -15322, -15416, -15510, -15604, -15698,
		  -15792, -15886, -15980, -16074, -16168, -16262, -16356, -16450,
		  -16544, -16638, -16732, -16826, -16920, -17014, -17108, -17202,
		  -17296, -17390, -17484, -17578, -17672, -17766, -17860, -17954,
		  -18048, -18142, -18236, -18330, -18424, -18518, -18612, -18706,
		  -18800, -18894, -18988, -19082, -19176, -19270, -19364, -19458,
		  -19552, -19646, -19740, -19834, -19928, -20022, -20116, -20210,
		  -20304, -20398, -20492, -20586, -20680, -20774, -20868, -20962,
		  -21056, -21150, -21244, -21338, -21432, -21526, -21620, -21714,
		  -21808, -21902, -21996, -22090, -22184, -22278, -22372, -22466,
		  -22560, -22654, -22748, -22842, -22936, -23030, -23124, -23218,
		  -23312, -23406, -23500, -23594, -23688, -23782, -23876, -23970
		};
	int lookup_m_74[] = {
		      0,    -74,   -148,   -222,   -296,   -370,   -444,   -518,
		    -592,   -666,   -740,   -814,   -888,   -962,  -1036,  -1110,
		  -1184,  -1258,  -1332,  -1406,  -1480,  -1554,  -1628,  -1702,
		  -1776,  -1850,  -1924,  -1998,  -2072,  -2146,  -2220,  -2294,
		  -2368,  -2442,  -2516,  -2590,  -2664,  -2738,  -2812,  -2886,
		  -2960,  -3034,  -3108,  -3182,  -3256,  -3330,  -3404,  -3478,
		  -3552,  -3626,  -3700,  -3774,  -3848,  -3922,  -3996,  -4070,
		  -4144,  -4218,  -4292,  -4366,  -4440,  -4514,  -4588,  -4662,
		  -4736,  -4810,  -4884,  -4958,  -5032,  -5106,  -5180,  -5254,
		  -5328,  -5402,  -5476,  -5550,  -5624,  -5698,  -5772,  -5846,
		  -5920,  -5994,  -6068,  -6142,  -6216,  -6290,  -6364,  -6438,
		  -6512,  -6586,  -6660,  -6734,  -6808,  -6882,  -6956,  -7030,
		  -7104,  -7178,  -7252,  -7326,  -7400,  -7474,  -7548,  -7622,
		  -7696,  -7770,  -7844,  -7918,  -7992,  -8066,  -8140,  -8214,
		  -8288,  -8362,  -8436,  -8510,  -8584,  -8658,  -8732,  -8806,
		  -8880,  -8954,  -9028,  -9102,  -9176,  -9250,  -9324,  -9398,
		  -9472,  -9546,  -9620,  -9694,  -9768,  -9842,  -9916,  -9990,
		  -10064, -10138, -10212, -10286, -10360, -10434, -10508, -10582,
		  -10656, -10730, -10804, -10878, -10952, -11026, -11100, -11174,
		  -11248, -11322, -11396, -11470, -11544, -11618, -11692, -11766,
		  -11840, -11914, -11988, -12062, -12136, -12210, -12284, -12358,
		  -12432, -12506, -12580, -12654, -12728, -12802, -12876, -12950,
		  -13024, -13098, -13172, -13246, -13320, -13394, -13468, -13542,
		  -13616, -13690, -13764, -13838, -13912, -13986, -14060, -14134,
		  -14208, -14282, -14356, -14430, -14504, -14578, -14652, -14726,
		  -14800, -14874, -14948, -15022, -15096, -15170, -15244, -15318,
		  -15392, -15466, -15540, -15614, -15688, -15762, -15836, -15910,
		  -15984, -16058, -16132, -16206, -16280, -16354, -16428, -16502,
		  -16576, -16650, -16724, -16798, -16872, -16946, -17020, -17094,
		  -17168, -17242, -17316, -17390, -17464, -17538, -17612, -17686,
		  -17760, -17834, -17908, -17982, -18056, -18130, -18204, -18278,
		  -18352, -18426, -18500, -18574, -18648, -18722, -18796, -18870
		};
	int lookup_m_38[] = {
		      0,    -38,    -76,   -114,   -152,   -190,   -228,   -266,
		    -304,   -342,   -380,   -418,   -456,   -494,   -532,   -570,
		    -608,   -646,   -684,   -722,   -760,   -798,   -836,   -874,
		    -912,   -950,   -988,  -1026,  -1064,  -1102,  -1140,  -1178,
		  -1216,  -1254,  -1292,  -1330,  -1368,  -1406,  -1444,  -1482,
		  -1520,  -1558,  -1596,  -1634,  -1672,  -1710,  -1748,  -1786,
		  -1824,  -1862,  -1900,  -1938,  -1976,  -2014,  -2052,  -2090,
		  -2128,  -2166,  -2204,  -2242,  -2280,  -2318,  -2356,  -2394,
		  -2432,  -2470,  -2508,  -2546,  -2584,  -2622,  -2660,  -2698,
		  -2736,  -2774,  -2812,  -2850,  -2888,  -2926,  -2964,  -3002,
		  -3040,  -3078,  -3116,  -3154,  -3192,  -3230,  -3268,  -3306,
		  -3344,  -3382,  -3420,  -3458,  -3496,  -3534,  -3572,  -3610,
		  -3648,  -3686,  -3724,  -3762,  -3800,  -3838,  -3876,  -3914,
		  -3952,  -3990,  -4028,  -4066,  -4104,  -4142,  -4180,  -4218,
		  -4256,  -4294,  -4332,  -4370,  -4408,  -4446,  -4484,  -4522,
		  -4560,  -4598,  -4636,  -4674,  -4712,  -4750,  -4788,  -4826,
		  -4864,  -4902,  -4940,  -4978,  -5016,  -5054,  -5092,  -5130,
		  -5168,  -5206,  -5244,  -5282,  -5320,  -5358,  -5396,  -5434,
		  -5472,  -5510,  -5548,  -5586,  -5624,  -5662,  -5700,  -5738,
		  -5776,  -5814,  -5852,  -5890,  -5928,  -5966,  -6004,  -6042,
		  -6080,  -6118,  -6156,  -6194,  -6232,  -6270,  -6308,  -6346,
		  -6384,  -6422,  -6460,  -6498,  -6536,  -6574,  -6612,  -6650,
		  -6688,  -6726,  -6764,  -6802,  -6840,  -6878,  -6916,  -6954,
		  -6992,  -7030,  -7068,  -7106,  -7144,  -7182,  -7220,  -7258,
		  -7296,  -7334,  -7372,  -7410,  -7448,  -7486,  -7524,  -7562,
		  -7600,  -7638,  -7676,  -7714,  -7752,  -7790,  -7828,  -7866,
		  -7904,  -7942,  -7980,  -8018,  -8056,  -8094,  -8132,  -8170,
		  -8208,  -8246,  -8284,  -8322,  -8360,  -8398,  -8436,  -8474,
		  -8512,  -8550,  -8588,  -8626,  -8664,  -8702,  -8740,  -8778,
		  -8816,  -8854,  -8892,  -8930,  -8968,  -9006,  -9044,  -9082,
		  -9120,  -9158,  -9196,  -9234,  -9272,  -9310,  -9348,  -9386,
		  -9424,  -9462,  -9500,  -9538,  -9576,  -9614,  -9652,  -9690
		};
	int lookup_m_18[] = {
		      0,    -18,    -36,    -54,    -72,    -90,   -108,   -126,
		    -144,   -162,   -180,   -198,   -216,   -234,   -252,   -270,
		    -288,   -306,   -324,   -342,   -360,   -378,   -396,   -414,
		    -432,   -450,   -468,   -486,   -504,   -522,   -540,   -558,
		    -576,   -594,   -612,   -630,   -648,   -666,   -684,   -702,
		    -720,   -738,   -756,   -774,   -792,   -810,   -828,   -846,
		    -864,   -882,   -900,   -918,   -936,   -954,   -972,   -990,
		  -1008,  -1026,  -1044,  -1062,  -1080,  -1098,  -1116,  -1134,
		  -1152,  -1170,  -1188,  -1206,  -1224,  -1242,  -1260,  -1278,
		  -1296,  -1314,  -1332,  -1350,  -1368,  -1386,  -1404,  -1422,
		  -1440,  -1458,  -1476,  -1494,  -1512,  -1530,  -1548,  -1566,
		  -1584,  -1602,  -1620,  -1638,  -1656,  -1674,  -1692,  -1710,
		  -1728,  -1746,  -1764,  -1782,  -1800,  -1818,  -1836,  -1854,
		  -1872,  -1890,  -1908,  -1926,  -1944,  -1962,  -1980,  -1998,
		  -2016,  -2034,  -2052,  -2070,  -2088,  -2106,  -2124,  -2142,
		  -2160,  -2178,  -2196,  -2214,  -2232,  -2250,  -2268,  -2286,
		  -2304,  -2322,  -2340,  -2358,  -2376,  -2394,  -2412,  -2430,
		  -2448,  -2466,  -2484,  -2502,  -2520,  -2538,  -2556,  -2574,
		  -2592,  -2610,  -2628,  -2646,  -2664,  -2682,  -2700,  -2718,
		  -2736,  -2754,  -2772,  -2790,  -2808,  -2826,  -2844,  -2862,
		  -2880,  -2898,  -2916,  -2934,  -2952,  -2970,  -2988,  -3006,
		  -3024,  -3042,  -3060,  -3078,  -3096,  -3114,  -3132,  -3150,
		  -3168,  -3186,  -3204,  -3222,  -3240,  -3258,  -3276,  -3294,
		  -3312,  -3330,  -3348,  -3366,  -3384,  -3402,  -3420,  -3438,
		  -3456,  -3474,  -3492,  -3510,  -3528,  -3546,  -3564,  -3582,
		  -3600,  -3618,  -3636,  -3654,  -3672,  -3690,  -3708,  -3726,
		  -3744,  -3762,  -3780,  -3798,  -3816,  -3834,  -3852,  -3870,
		  -3888,  -3906,  -3924,  -3942,  -3960,  -3978,  -3996,  -4014,
		  -4032,  -4050,  -4068,  -4086,  -4104,  -4122,  -4140,  -4158,
		  -4176,  -4194,  -4212,  -4230,  -4248,  -4266,  -4284,  -4302,
		  -4320,  -4338,  -4356,  -4374,  -4392,  -4410,  -4428,  -4446,
		  -4464,  -4482,  -4500,  -4518,  -4536,  -4554,  -4572,  -4590
		};
	int lookup25[] = {
		      0,     25,     50,     75,    100,    125,    150,    175,
		    200,    225,    250,    275,    300,    325,    350,    375,
		    400,    425,    450,    475,    500,    525,    550,    575,
		    600,    625,    650,    675,    700,    725,    750,    775,
		    800,    825,    850,    875,    900,    925,    950,    975,
		    1000,   1025,   1050,   1075,   1100,   1125,   1150,   1175,
		    1200,   1225,   1250,   1275,   1300,   1325,   1350,   1375,
		    1400,   1425,   1450,   1475,   1500,   1525,   1550,   1575,
		    1600,   1625,   1650,   1675,   1700,   1725,   1750,   1775,
		    1800,   1825,   1850,   1875,   1900,   1925,   1950,   1975,
		    2000,   2025,   2050,   2075,   2100,   2125,   2150,   2175,
		    2200,   2225,   2250,   2275,   2300,   2325,   2350,   2375,
		    2400,   2425,   2450,   2475,   2500,   2525,   2550,   2575,
		    2600,   2625,   2650,   2675,   2700,   2725,   2750,   2775,
		    2800,   2825,   2850,   2875,   2900,   2925,   2950,   2975,
		    3000,   3025,   3050,   3075,   3100,   3125,   3150,   3175,
		    3200,   3225,   3250,   3275,   3300,   3325,   3350,   3375,
		    3400,   3425,   3450,   3475,   3500,   3525,   3550,   3575,
		    3600,   3625,   3650,   3675,   3700,   3725,   3750,   3775,
		    3800,   3825,   3850,   3875,   3900,   3925,   3950,   3975,
		    4000,   4025,   4050,   4075,   4100,   4125,   4150,   4175,
		    4200,   4225,   4250,   4275,   4300,   4325,   4350,   4375,
		    4400,   4425,   4450,   4475,   4500,   4525,   4550,   4575,
		    4600,   4625,   4650,   4675,   4700,   4725,   4750,   4775,
		    4800,   4825,   4850,   4875,   4900,   4925,   4950,   4975,
		    5000,   5025,   5050,   5075,   5100,   5125,   5150,   5175,
		    5200,   5225,   5250,   5275,   5300,   5325,   5350,   5375,
		    5400,   5425,   5450,   5475,   5500,   5525,   5550,   5575,
		    5600,   5625,   5650,   5675,   5700,   5725,   5750,   5775,
		    5800,   5825,   5850,   5875,   5900,   5925,   5950,   5975,
		    6000,   6025,   6050,   6075,   6100,   6125,   6150,   6175,
		    6200,   6225,   6250,   6275,   6300,   6325,   6350,   6375
		};
	int lookup66[] = {
		      0,     66,    132,    198,    264,    330,    396,    462,
		    528,    594,    660,    726,    792,    858,    924,    990,
		    1056,   1122,   1188,   1254,   1320,   1386,   1452,   1518,
		    1584,   1650,   1716,   1782,   1848,   1914,   1980,   2046,
		    2112,   2178,   2244,   2310,   2376,   2442,   2508,   2574,
		    2640,   2706,   2772,   2838,   2904,   2970,   3036,   3102,
		    3168,   3234,   3300,   3366,   3432,   3498,   3564,   3630,
		    3696,   3762,   3828,   3894,   3960,   4026,   4092,   4158,
		    4224,   4290,   4356,   4422,   4488,   4554,   4620,   4686,
		    4752,   4818,   4884,   4950,   5016,   5082,   5148,   5214,
		    5280,   5346,   5412,   5478,   5544,   5610,   5676,   5742,
		    5808,   5874,   5940,   6006,   6072,   6138,   6204,   6270,
		    6336,   6402,   6468,   6534,   6600,   6666,   6732,   6798,
		    6864,   6930,   6996,   7062,   7128,   7194,   7260,   7326,
		    7392,   7458,   7524,   7590,   7656,   7722,   7788,   7854,
		    7920,   7986,   8052,   8118,   8184,   8250,   8316,   8382,
		    8448,   8514,   8580,   8646,   8712,   8778,   8844,   8910,
		    8976,   9042,   9108,   9174,   9240,   9306,   9372,   9438,
		    9504,   9570,   9636,   9702,   9768,   9834,   9900,   9966,
		  10032,  10098,  10164,  10230,  10296,  10362,  10428,  10494,
		  10560,  10626,  10692,  10758,  10824,  10890,  10956,  11022,
		  11088,  11154,  11220,  11286,  11352,  11418,  11484,  11550,
		  11616,  11682,  11748,  11814,  11880,  11946,  12012,  12078,
		  12144,  12210,  12276,  12342,  12408,  12474,  12540,  12606,
		  12672,  12738,  12804,  12870,  12936,  13002,  13068,  13134,
		  13200,  13266,  13332,  13398,  13464,  13530,  13596,  13662,
		  13728,  13794,  13860,  13926,  13992,  14058,  14124,  14190,
		  14256,  14322,  14388,  14454,  14520,  14586,  14652,  14718,
		  14784,  14850,  14916,  14982,  15048,  15114,  15180,  15246,
		  15312,  15378,  15444,  15510,  15576,  15642,  15708,  15774,
		  15840,  15906,  15972,  16038,  16104,  16170,  16236,  16302,
		  16368,  16434,  16500,  16566,  16632,  16698,  16764,  16830
		};
	int lookup112[] = {
		      0,    112,    224,    336,    448,    560,    672,    784,
		    896,   1008,   1120,   1232,   1344,   1456,   1568,   1680,
		    1792,   1904,   2016,   2128,   2240,   2352,   2464,   2576,
		    2688,   2800,   2912,   3024,   3136,   3248,   3360,   3472,
		    3584,   3696,   3808,   3920,   4032,   4144,   4256,   4368,
		    4480,   4592,   4704,   4816,   4928,   5040,   5152,   5264,
		    5376,   5488,   5600,   5712,   5824,   5936,   6048,   6160,
		    6272,   6384,   6496,   6608,   6720,   6832,   6944,   7056,
		    7168,   7280,   7392,   7504,   7616,   7728,   7840,   7952,
		    8064,   8176,   8288,   8400,   8512,   8624,   8736,   8848,
		    8960,   9072,   9184,   9296,   9408,   9520,   9632,   9744,
		    9856,   9968,  10080,  10192,  10304,  10416,  10528,  10640,
		  10752,  10864,  10976,  11088,  11200,  11312,  11424,  11536,
		  11648,  11760,  11872,  11984,  12096,  12208,  12320,  12432,
		  12544,  12656,  12768,  12880,  12992,  13104,  13216,  13328,
		  13440,  13552,  13664,  13776,  13888,  14000,  14112,  14224,
		  14336,  14448,  14560,  14672,  14784,  14896,  15008,  15120,
		  15232,  15344,  15456,  15568,  15680,  15792,  15904,  16016,
		  16128,  16240,  16352,  16464,  16576,  16688,  16800,  16912,
		  17024,  17136,  17248,  17360,  17472,  17584,  17696,  17808,
		  17920,  18032,  18144,  18256,  18368,  18480,  18592,  18704,
		  18816,  18928,  19040,  19152,  19264,  19376,  19488,  19600,
		  19712,  19824,  19936,  20048,  20160,  20272,  20384,  20496,
		  20608,  20720,  20832,  20944,  21056,  21168,  21280,  21392,
		  21504,  21616,  21728,  21840,  21952,  22064,  22176,  22288,
		  22400,  22512,  22624,  22736,  22848,  22960,  23072,  23184,
		  23296,  23408,  23520,  23632,  23744,  23856,  23968,  24080,
		  24192,  24304,  24416,  24528,  24640,  24752,  24864,  24976,
		  25088,  25200,  25312,  25424,  25536,  25648,  25760,  25872,
		  25984,  26096,  26208,  26320,  26432,  26544,  26656,  26768,
		  26880,  26992,  27104,  27216,  27328,  27440,  27552,  27664,
		  27776,  27888,  28000,  28112,  28224,  28336,  28448,  28560
		};
	int lookup129[] = {
		      0,    129,    258,    387,    516,    645,    774,    903,
		    1032,   1161,   1290,   1419,   1548,   1677,   1806,   1935,
		    2064,   2193,   2322,   2451,   2580,   2709,   2838,   2967,
		    3096,   3225,   3354,   3483,   3612,   3741,   3870,   3999,
		    4128,   4257,   4386,   4515,   4644,   4773,   4902,   5031,
		    5160,   5289,   5418,   5547,   5676,   5805,   5934,   6063,
		    6192,   6321,   6450,   6579,   6708,   6837,   6966,   7095,
		    7224,   7353,   7482,   7611,   7740,   7869,   7998,   8127,
		    8256,   8385,   8514,   8643,   8772,   8901,   9030,   9159,
		    9288,   9417,   9546,   9675,   9804,   9933,  10062,  10191,
		  10320,  10449,  10578,  10707,  10836,  10965,  11094,  11223,
		  11352,  11481,  11610,  11739,  11868,  11997,  12126,  12255,
		  12384,  12513,  12642,  12771,  12900,  13029,  13158,  13287,
		  13416,  13545,  13674,  13803,  13932,  14061,  14190,  14319,
		  14448,  14577,  14706,  14835,  14964,  15093,  15222,  15351,
		  15480,  15609,  15738,  15867,  15996,  16125,  16254,  16383,
		  16512,  16641,  16770,  16899,  17028,  17157,  17286,  17415,
		  17544,  17673,  17802,  17931,  18060,  18189,  18318,  18447,
		  18576,  18705,  18834,  18963,  19092,  19221,  19350,  19479,
		  19608,  19737,  19866,  19995,  20124,  20253,  20382,  20511,
		  20640,  20769,  20898,  21027,  21156,  21285,  21414,  21543,
		  21672,  21801,  21930,  22059,  22188,  22317,  22446,  22575,
		  22704,  22833,  22962,  23091,  23220,  23349,  23478,  23607,
		  23736,  23865,  23994,  24123,  24252,  24381,  24510,  24639,
		  24768,  24897,  25026,  25155,  25284,  25413,  25542,  25671,
		  25800,  25929,  26058,  26187,  26316,  26445,  26574,  26703,
		  26832,  26961,  27090,  27219,  27348,  27477,  27606,  27735,
		  27864,  27993,  28122,  28251,  28380,  28509,  28638,  28767,
		  28896,  29025,  29154,  29283,  29412,  29541,  29670,  29799,
		  29928,  30057,  30186,  30315,  30444,  30573,  30702,  30831,
		  30960,  31089,  31218,  31347,  31476,  31605,  31734,  31863,
		  31992,  32121,  32250,  32379,  32508,  32637,  32766,  32895
		};

}
