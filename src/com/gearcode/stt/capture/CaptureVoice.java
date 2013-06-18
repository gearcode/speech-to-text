package com.gearcode.stt.capture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public class CaptureVoice {
	
	/**
	 * 状态
	 */
	public enum CaptureState {
    	CAPTURING, STOP
	}
    
	/**
	 * 音频格式
	 */
	public static final AudioFormat format = new AudioFormat(8000.0f, 16, 1,
			true, false);

	/**
	 * 用来计算声音大小的常量
	 */
	public static final float MAX_8_BITS_SIGNED = Byte.MAX_VALUE;
	public static final float MAX_8_BITS_UNSIGNED = 0xff;
	public static final float MAX_16_BITS_SIGNED = Short.MAX_VALUE;
	public static final float MAX_16_BITS_UNSIGNED = 0xffff;

	/**
	 * 每个声音片段的间隔时间
	 */
	public final long WORD_GAPS = 1500;
	
	/**
	 * 声音的音量最小值，小于此值则忽略
	 */
	public final int AUDIO_LEVEL_MIN = 8;

	public CaptureState state = CaptureState.STOP;
	
	public VoiceLevelListener levelListener;
	public VoiceClipListener clipListener;
	
    private TargetDataLine targetDataLine;
	
	/**
	 * 开始捕捉音频
	 * @throws LineUnavailableException 
	 */
	public void start() throws LineUnavailableException {
		//设置状态为捕捉ing
		state = CaptureState.CAPTURING;
		
		//设置targetLine
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, CaptureVoice.format);
        targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
        
        //创建新线程以捕捉音频
		new Thread(new CaptureThread()).run();
	}

	/**
	 * 停止捕捉音频
	 */
	public void stop() {
		state = CaptureState.STOP;
		targetDataLine.stop();
		targetDataLine.close();
	}
	
	private class CaptureThread implements Runnable {

		public void run() {
			
			int frameSizeInBytes = format.getFrameSize();
			int bufferLengthInFrames = targetDataLine.getBufferSize() / 8;
			int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
			byte[] bytes = new byte[bufferLengthInBytes];
			@SuppressWarnings("unused")
			int numBytesRead;
			
			/**
			 * 截取音频片段使用的变量
			 */
			long start = -1;
			long gap = -1;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			
			try {
				targetDataLine.open(CaptureVoice.format);
				targetDataLine.start();
			} catch (LineUnavailableException e) {
				e.printStackTrace();
			}

			while (state.equals(CaptureState.CAPTURING)) {
				//读取音频
				if((numBytesRead = targetDataLine.read(bytes, 0, bytes.length)) == -1) {
					break;
				}
				
				/**
				 * 计算level
				 */
				int level = (int)(CaptureVoice.calculateLevel(bytes, 0, 0) * 100);
				if(levelListener != null) {
					levelListener.captureLevel(level);
				}
				
				/**
				 * 截取片段
				 */
				long cur = System.currentTimeMillis();
				
				if (level > AUDIO_LEVEL_MIN) {
					/*
					 * 监听到声音
					 */
					gap = -1;
					if (start == -1) {
						start = cur;
						out = new ByteArrayOutputStream();
					}
					
					try {
						out.write(bytes);
					} catch (IOException e) {
						e.printStackTrace();
					}

				} else {
					/*
					 * 无声音
					 */
					if (start != -1) {
						if (gap == -1) {
							gap = cur;
						}

						//
						if (cur - gap > WORD_GAPS) {
							System.out.println("长度：" + (cur - start));
							start = -1;
							gap = -1;
							
							byte[] byteArray = out.toByteArray();
							
							AudioInputStream audioInputStream = new AudioInputStream(
									new ByteArrayInputStream(byteArray),
									CaptureVoice.format,
									byteArray.length / CaptureVoice.format.getFrameSize());
						
							if(clipListener != null) {
								clipListener.captureClip(audioInputStream);
							}
						}
					}
				}
			}
                
        }
		
	}

	/**
	 * 计算声音大小
	 * 
	 * @param buffer
	 * @param readPoint
	 * @param leftOver
	 * @return
	 */
	public static float calculateLevel(byte[] buffer, int readPoint,
			int leftOver) {
		int max = 0;
		float level;
		boolean use16Bit = (CaptureVoice.format.getSampleSizeInBits() == 16);
		boolean signed = (CaptureVoice.format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED);
		boolean bigEndian = (CaptureVoice.format.isBigEndian());

		if (use16Bit) {
			for (int i = readPoint; i < buffer.length - leftOver; i += 2) {
				int value = 0;
				// deal with endianness
				int hiByte = (bigEndian ? buffer[i] : buffer[i + 1]);
				int loByte = (bigEndian ? buffer[i + 1] : buffer[i]);
				if (signed) {
					short shortVal = (short) hiByte;
					shortVal = (short) ((shortVal << 8) | (byte) loByte);
					value = shortVal;
				} else {
					value = (hiByte << 8) | loByte;
				}
				max = Math.max(max, value);
			} // for
		} else {
			// 8 bit - no endianness issues, just sign
			for (int i = readPoint; i < buffer.length - leftOver; i++) {
				int value = 0;
				if (signed) {
					value = buffer[i];
				} else {
					short shortVal = 0;
					shortVal = (short) (shortVal | buffer[i]);
					value = shortVal;
				}
				max = Math.max(max, value);
			} // for
		} // 8 bit
			// express max as float of 0.0 to 1.0 of max value
		// of 8 or 16 bits (signed or unsigned)
		if (signed) {
			if (use16Bit) {
				level = (float) max / MAX_16_BITS_SIGNED;
			} else {
				level = (float) max / MAX_8_BITS_SIGNED;
			}
		} else {
			if (use16Bit) {
				level = (float) max / MAX_16_BITS_UNSIGNED;
			} else {
				level = (float) max / MAX_8_BITS_UNSIGNED;
			}
		}

		return level;
	}
	
}
