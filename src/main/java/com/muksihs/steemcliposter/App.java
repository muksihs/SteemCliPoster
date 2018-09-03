package com.muksihs.steemcliposter;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.cherokeelessons.gui.AbstractApp;
import com.cherokeelessons.gui.MainWindow.Config;

public class App extends AbstractApp implements Runnable {

	private static final NumberFormat NF = NumberFormat.getInstance();

	public App(Config config, String[] args) {
		super(config, args);
	}

	/**
	 * List of files to post.
	 */
	private final List<File> postFiles = new ArrayList<>();
	
	/**
	 * Java properties file to obtain <strong>posting</strong> key and account name from.
	 */
	private File authFile=null;

	/**
	 * If true, steem API errors are silently ignored.
	 */
	private boolean ignoreErrors=false;

	/**
	 * If true, do not do basic "<, >, &" xhtml escaping.
	 */
	private boolean noEscape=false;

	/**
	 * If true, perform a basic HTML-ification of the message body.
	 */
	private boolean htmlify=false;
	
	@Override
	protected void parseArgs(Iterator<String> iargs) {
		while (iargs.hasNext()) {
			String arg = iargs.next();
			switch (arg) {
			case "--htmlify":
				htmlify=true;
				throw new IllegalArgumentException("HTML-ificaton not implemented yet.");				
//				break;
			case "--no-escape":
				noEscape=true;
				break;
			case "--ignore-errors":
				ignoreErrors = true;
				break;
			case "--file":
				if (!iargs.hasNext()) {
					throw new IllegalArgumentException("You must provide a filename for --file.");
				}
				File tmp = new File(iargs.next());
				if (authFile.isDirectory()) {
					throw new IllegalArgumentException("File to be posted can not be a directory: "+authFile.getAbsolutePath());
				}
				if (!authFile.canRead()) {
					throw new IllegalArgumentException("File to be posted is missing or unreadable: "+authFile.getAbsolutePath());
				}
				postFiles.add(tmp);
				break;
			case "--auth-file":
				if (!iargs.hasNext()) {
					throw new IllegalArgumentException("You must provide an auth file.");
				}
				authFile=new File(iargs.next());
				if (authFile.isDirectory()) {
					throw new IllegalArgumentException("Auth file can not be a directory: "+authFile.getAbsolutePath());
				}
				if (!authFile.canRead()) {
					throw new IllegalArgumentException("Missing or unreadable auth file: "+authFile.getAbsolutePath());
				}
				break;
			default:
				throw new IllegalArgumentException("Unknown CLI option: "+arg);
			}
		}
		if (authFile==null) {
			throw new IllegalArgumentException("You must provide an auth file using --auth-file <FILE-NAME>.");
		}
		String fileOrFiles = (postFiles.size()==1?"file":"files");
		log.info("Have "+NF.format(postFiles.size())+" "+fileOrFiles+" to post.");
		if (postFiles.isEmpty()) {
			throw new IllegalArgumentException("You must provide at least one file to post with --file. You can specify multiple files by using multiple --file <FILE> options.");
		}
	}

	@Override
	protected void execute() throws IOException, SecurityException, Exception {
		// TODO Auto-generated method stub
		
	}

}
