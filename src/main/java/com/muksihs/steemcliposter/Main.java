package com.muksihs.steemcliposter;

import com.cherokeelessons.gui.MainWindow;
import com.cherokeelessons.gui.MainWindow.Config;

public class Main {

	public static void main(String[] args) {
		Config config = new Config() {
			@Override
			public String getApptitle() {
				return "Steem CLI Poster";
			}

			@Override
			public Runnable getApp(String... args) throws Exception {
				return new App(this, args);
			}
		};
		MainWindow.init(config, args);
	}

}
