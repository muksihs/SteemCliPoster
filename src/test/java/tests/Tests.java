package tests;

import org.testng.annotations.Test;

import com.muksihs.steemcliposter.App;

public class Tests {
	@Test
	public void reformatTest() {
		String testBody = "This is a test message with     weird spacing.\n\n&paragraph <2>.";
		String result = App.htmlify(testBody);
		System.out.println(result);
	}
}
