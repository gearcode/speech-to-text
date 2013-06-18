package com.gearcode.stt.main;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javaFlacEncoder.FLACStreamOutputStream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.LineUnavailableException;

import com.gearcode.stt.capture.CaptureVoice;
import com.gearcode.stt.capture.VoiceClipListener;
import com.gearcode.stt.capture.VoiceLevelListener;
import com.gearcode.stt.flac.FlacEncoder;
import com.gearcode.stt.recognizer.GoogleResponse;
import com.gearcode.stt.recognizer.Recognizer;

public class Main {

	public static void main(String[] args) throws LineUnavailableException {
		final CaptureVoice captureVoice = new CaptureVoice();
		/*
		 * 监听声音大小变化
		 */
		captureVoice.levelListener = new VoiceLevelListener() {
			public void captureLevel(int level) {
				System.out.println(level);
			}
		};
		/*
		 * 监听到声音片段
		 */
		captureVoice.clipListener = new VoiceClipListener() {
			public void captureClip(AudioInputStream clipAIS) {
				/*
				 * convert waveStream to flacStream
				 */
				ByteArrayOutputStream flacOS = new ByteArrayOutputStream();
				FlacEncoder flacEncoder = new FlacEncoder();
				try {
					flacEncoder.convertWaveToFlac(clipAIS, new FLACStreamOutputStream(flacOS));
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				/*
				 * 语音识别
				 */
				Recognizer recognizer = new Recognizer();
				GoogleResponse response = null;
				try {
					response = recognizer.getRecognizedDataForFlac(flacOS.toByteArray());
				} catch (Exception e) {
					e.printStackTrace();
				}
				if(response != null) {
					System.out.println(response.getResponse() + " " + response.getConfidence());
				}
			}
		};
		
		captureVoice.start();
	}

}
