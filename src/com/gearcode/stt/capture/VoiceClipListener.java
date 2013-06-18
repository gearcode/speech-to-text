package com.gearcode.stt.capture;

import javax.sound.sampled.AudioInputStream;

public interface VoiceClipListener {

	/**
	 * 捕捉到的音频流
	 * 
	 * @param ais
	 */
	public void captureClip(AudioInputStream clipAIS);
}
