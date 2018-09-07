package com.muksihs.steemcliposter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import com.cherokeelessons.gui.AbstractApp;
import com.cherokeelessons.gui.MainWindow.Config;
import com.muksihs.steemcliposter.model.PostData;
import com.muksihs.steemcliposter.model.SteemAccountInformation;

import eu.bittrade.libs.steemj.SteemJ;
import eu.bittrade.libs.steemj.base.models.AccountName;
import eu.bittrade.libs.steemj.base.models.ExtendedAccount;
import eu.bittrade.libs.steemj.base.models.TimePointSec;
import eu.bittrade.libs.steemj.configuration.SteemJConfig;
import eu.bittrade.libs.steemj.enums.PrivateKeyType;
import eu.bittrade.libs.steemj.exceptions.SteemCommunicationException;
import eu.bittrade.libs.steemj.exceptions.SteemResponseException;

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
	 * Java properties file to obtain <strong>posting</strong> key and account name
	 * from.
	 */
	private File authFile = null;

	/**
	 * If true, steem API errors are silently ignored.
	 */
	@SuppressWarnings("unused")
	private boolean ignoreErrors = false;

	/**
	 * If true, do not do basic "<, >, &" xhtml escaping.
	 */
	private boolean noEscape = false;

	/**
	 * If true, perform a basic HTML-ification of the message body.<br>
	 * Basically just does a wrapping in &lt;html&gt;, considers double '\n\n' as
	 * &lt;p&gt; marks, and converts consequetive spaces into space nbsp pairings
	 * (to preserve spacing formating some)
	 */
	private boolean htmlify = false;

	@Override
	protected void parseArgs(Iterator<String> iargs) {
		while (iargs.hasNext()) {
			String arg = iargs.next();
			switch (arg) {
			case "--htmlify":
				htmlify = true;
				throw new IllegalArgumentException("HTML-ificaton not implemented yet.");
				// break;
			case "--no-escape":
				noEscape = true;
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
					throw new IllegalArgumentException(
							"File to be posted can not be a directory: " + authFile.getAbsolutePath());
				}
				if (!authFile.canRead()) {
					throw new IllegalArgumentException(
							"File to be posted is missing or unreadable: " + authFile.getAbsolutePath());
				}
				postFiles.add(tmp);
				break;
			case "--auth-file":
				if (!iargs.hasNext()) {
					throw new IllegalArgumentException("You must provide an auth file.");
				}
				authFile = new File(iargs.next());
				if (authFile.isDirectory()) {
					throw new IllegalArgumentException(
							"Auth file can not be a directory: " + authFile.getAbsolutePath());
				}
				if (!authFile.canRead()) {
					throw new IllegalArgumentException(
							"Missing or unreadable auth file: " + authFile.getAbsolutePath());
				}
				break;
			default:
				throw new IllegalArgumentException("Unknown CLI option: " + arg);
			}
		}
		if (authFile == null) {
			throw new IllegalArgumentException("You must provide an auth file using --auth-file <FILE-NAME>.");
		}
		String fileOrFiles = (postFiles.size() == 1 ? "file" : "files");
		log.info("Have " + NF.format(postFiles.size()) + " " + fileOrFiles + " to post.");
		if (postFiles.isEmpty()) {
			throw new IllegalArgumentException(
					"You must provide at least one file to post with --file. You can specify multiple files by using multiple --file <FILE> options.");
		}
	}

	@Override
	protected void execute() throws IOException, SecurityException, Exception {
		Map<String, Object> extraMetadata = new HashMap<>();
		extraMetadata.put("app", "SteemCliPoster/20180903");
		SteemAccountInformation accountInfo = getKeyAuthData(authFile);
		SteemJ steemJ = initilizeSteemJ(accountInfo);
		for (File postFile : postFiles) {
			PostData post = parsePostFile(postFile);
			String format = post.getFormat();
			String[] tags = post.getTags().toArray(new String[0]);
			String content = post.getBody();
			String title = post.getTitle();
			waitCheckBeforePosting(steemJ);
			log.info("Posting: " + title);
			steemJ.createPost(title, content, tags, format, extraMetadata);
		}
	}

	/**
	 * Parses a message for posting from a UTF-8 file.<br>
	 * File are expected to have the following format IN ORDER:
	 * <ul>
	 * <li>TITLE: The title of the message. [LF]</li>
	 * <li>TAGS: tag1 tag2 tag3 ... [LF] (tags are lowercased!)</li>
	 * <li>FORMAT: markdown|markdown+html|text/html</li>
	 * <li>MULTI-LINE TEXT
	 * <li>
	 * </ul>
	 * 
	 * @param postFile
	 * @return
	 * @throws IOException
	 */
	private PostData parsePostFile(File postFile) throws IOException {
		LineIterator iter = FileUtils.lineIterator(postFile);
		PostData postData = new PostData();
		while (iter.hasNext()) {
			String line = iter.nextLine();
			if (line.trim().isEmpty()) {
				continue;
			}
			if (!line.toLowerCase().startsWith("title:")) {
				throw new IllegalArgumentException("Missing TITLE at start of message: " + postFile.getAbsolutePath());
			}
			postData.setTitle(line.substring(6).trim());
			break;
		}
		while (iter.hasNext()) {
			String line = iter.nextLine();
			if (line.trim().isEmpty()) {
				continue;
			}
			if (!line.toLowerCase().startsWith("tags:")) {
				throw new IllegalArgumentException("Missing TAGS at start of message: " + postFile.getAbsolutePath());
			}
			line = line.substring(5).trim();
			String[] tags = line.split("[,\\s]");
			if (tags == null) {
				tags = new String[] { line };
			}
			for (String tag : tags) {
				tag = StringUtils.strip(tag, ", ").toLowerCase();
				if (tag.trim().isEmpty()) {
					continue;
				}
				if (postData.getTags().contains(tag)) {
					continue;
				}
				if (StringUtils.countMatches(tag, "-") > 1) {
					throw new IllegalArgumentException("Tags may have no more than one hyphen.");
				}
				if (tag.startsWith("-") || tag.endsWith("-")) {
					throw new IllegalArgumentException("Tags may not start or end with a hyphen.");
				}
				if (!tag.matches("[a-z].*")) {
					throw new IllegalArgumentException("Tags must start with a-z.");
				}
				if (!tag.equals(tag.replaceAll("[^a-z0-9\\-_]", ""))) {
					throw new IllegalArgumentException("Only a-z, 0-9, -, and _ are allowed in tags.");
				}
				postData.getTags().add(tag);
			}
			break;
		}
		while (iter.hasNext()) {
			String line = iter.nextLine();
			if (line.trim().isEmpty()) {
				continue;
			}
			if (!line.toLowerCase().startsWith("format:")) {
				throw new IllegalArgumentException("Missing FORMAT at start of message: " + postFile.getAbsolutePath());
			}
			postData.setFormat(line.substring(7).trim());
			break;
		}
		StringBuilder sb = new StringBuilder();
		while (iter.hasNext()) {
			sb.append(iter.next());
			sb.append("\n");
		}
		String body = sb.toString().trim() + "\n";
		if (body.equals("\n")) {
			throw new IllegalArgumentException("Missing BODY of message: " + postFile.getAbsolutePath());
		}
		if (htmlify) {
			body = htmlify(body);
			postData.setFormat("text/html");
		} else {
			if (!noEscape) {
				body = body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
			}
		}
		postData.setBody(body);
		return postData;
	}

	public static String htmlify(String body) {
		body = body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		body = body.replaceAll("  ", "&nbsp; ");
		body = "\n\n"+body+"\n\n";
		body = body.replaceAll("\n\n+", "\n\n");
		for (String para: body.split("\n\n")) {
			if (para.trim().isEmpty()) {
				continue;
			}
			body = body.replace("\n\n"+para, "<p>"+para+"</p>");
		}
		body = body.trim();
		body = body.replace("\n", "<br/>");
		return "<html>"+body+"</html>\n";
	}

	private void waitCheckBeforePosting(SteemJ steemJ) throws SteemCommunicationException, SteemResponseException {
		long FIVE_MINUTES = 1000l * 60l * 5l;
		SteemJConfig config = SteemJConfig.getInstance();
		AccountName account = config.getDefaultAccount();
		while (true) {
			List<ExtendedAccount> info = steemJ.getAccounts(Arrays.asList(account));
			TimePointSec now = steemJ.getDynamicGlobalProperties().getTime();
			TimePointSec lastPostTime = now;
			for (ExtendedAccount e : info) {
				lastPostTime = e.getLastRootPost();
				break;
			}
			long since = now.getDateTimeAsTimestamp() - lastPostTime.getDateTimeAsTimestamp();
			if (since > FIVE_MINUTES) {
				return;
			}
			long sleepFor = FIVE_MINUTES - since + 1000l;
			log.info("Last post was within 5 minutes. Sleeping " + NF.format(sleepFor / 60000f) + " minutes.");
			sleep(sleepFor);
		}
	}

	private void sleep(int sleep) {
		try {
			Thread.sleep(sleep);
		} catch (InterruptedException e1) {
		}
	}

	private SteemJ initilizeSteemJ(SteemAccountInformation accountInfo)
			throws SteemCommunicationException, SteemResponseException {
		SteemJConfig myConfig = SteemJConfig.getInstance();
		myConfig.setEncodingCharset(StandardCharsets.UTF_8);
		myConfig.setIdleTimeout(250);
		myConfig.setResponseTimeout(1000);
		myConfig.setBeneficiaryAccount(new AccountName("muksihs"));
//		myConfig.setSteemJWeight((short) 0);
		myConfig.setDefaultAccount(accountInfo.getAccountName());
		List<ImmutablePair<PrivateKeyType, String>> privateKeys = new ArrayList<>();
		if (!accountInfo.getActiveKey().trim().isEmpty()) {
			privateKeys.add(new ImmutablePair<>(PrivateKeyType.ACTIVE, accountInfo.getActiveKey()));
		}
		if (!accountInfo.getPostingKey().trim().isEmpty()) {
			privateKeys.add(new ImmutablePair<>(PrivateKeyType.POSTING, accountInfo.getPostingKey()));
		}
		myConfig.getPrivateKeyStorage().addAccount(myConfig.getDefaultAccount(), privateKeys);
		return new SteemJ();
	}

	private SteemAccountInformation getKeyAuthData(File authFile) throws FileNotFoundException, IOException {
		SteemAccountInformation account = new SteemAccountInformation();
		Properties steemConfig = new Properties();
		steemConfig.load(new FileInputStream(authFile));
		account.setActiveKey(steemConfig.getProperty("activeKey"));
		if (account.getActiveKey() == null || account.getActiveKey().trim().isEmpty()) {
			account.setActiveKey("");
		}
		account.setPostingKey(steemConfig.getProperty("postingKey"));
		if (account.getPostingKey() == null || account.getPostingKey().trim().isEmpty()) {
			account.setPostingKey("");
		}
		String tmp = steemConfig.getProperty("accountName");
		if (tmp == null || tmp.trim().isEmpty()) {
			throw new IllegalArgumentException("accountName= for steem account name not found");
		}
		account.setAccountName(new AccountName(tmp));
		if (account.getPostingKey() == "") {
			throw new IllegalArgumentException("Private posting key not found");
		}
		return account;
	}
}
