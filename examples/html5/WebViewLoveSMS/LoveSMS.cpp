/*
Copyright (C) 2011 MoSync AB

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License,
version 2, as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
MA 02110-1301, USA.
*/

/**
 * @file LoveSMS.cpp
 * @author Mikael Kindborg
 *
 * Application for sending Love SMSs.
 *
 * This program illustrates how to use WebView for the
 * user interface of a MoSync C++ application.
 *
 * An application can divide the program code between the
 * WebView layer and the C++ layer in a variety of ways.
 * In one extreme, the WebView is used purely for
 * rendering the user interface, the HTML/CSS/JavaScript
 * code could even be generated by C++ code. In the other
 * extreme, almost the entire application is written in
 * HTML/CSS/JavaScript, and only the calls needed to access
 * native functionality via the MoSync API is written in C++.
 *
 * Which approach is chosen depends on the preferences of the
 * development team, existing code and libraries, compatibility
 * considerations etc. Some teams may prefer to be C++ centric,
 * white others may prefer to do most of the development using
 * JavScript and web technologies.
 *
 * In this application, much of the application logic is written
 * in JavaScript, and the C++ layer is used for sending text
 * messages and for saving/loading the phone number entered
 * in the user interface. There is only one phone number saved,
 * because, after all, this is an application to be used with
 * your loved one. ;-)
 */

#include <ma.h>						    // MoSync API (base API).
#include <maheap.h>					    // C memory allocation functions.
#include <mastring.h>				    // C String functions.
#include <mavsprintf.h>				    // sprintf etc.
#include <Wormhole/WebAppMoblet.h>	    // Moblet for web applications.
#include <Wormhole/MessageStreamJSON.h>	// Messages from JavaScript.

using namespace MAUtil;
using namespace NativeUI;
using namespace Wormhole;

/**
 * Set to true to actually send SMS.
 * You can turn off SMS sending during debugging
 * by setting this variable to false.
 */
static bool sSendSMSForReal = true;

/**
 * The application class.
 */
class LoveSMSMoblet : public WebAppMoblet
{
public:
	LoveSMSMoblet()
	{
		// Enable message sending from JavaScript to C++.
		enableWebViewMessages();

		// Change this line to enableZoom to enable the
		// user to zoom the web page. To disable zoom is
		// one way of making web pages display in a
		// reasonable default size on devices with
		// different screen sizes.
		getWebView()->disableZoom();

		// The page in the "LocalFiles" folder to
		// show when the application starts.
		showPage("index.html");
	}

	/**
	 * This method handles messages sent from the WebView.
	 * @param webView The WebView that sent the message.
	 * @param urlData Data object that holds message content.
	 * Note that the data object will be valid only during
	 * the life-time of the call of this method, then it
	 * will be deallocated.
	 */
	void handleWebViewMessage(WebView* webView, MAHandle data)
	{
		MessageStreamJSON message(webView, data);

		while (message.next())
		{
			handleMessage(message);
		}
	}

	void handleMessage(MessageStreamJSON& message)
	{
		// Handle the message.
		if (message.is("SendSMS"))
		{
			// Save phone no and send SMS.
			savePhoneNoAndSendSMS(
				message.getParam("phoneNo"),
				message.getParam("message"));
		}
		else if (message.is("PageLoaded"))
		{
			// Load and set saved phone number.
			// We could alternatively use a JavaScript File API
			// to do this.
			setSavedPhoneNo();
		}
	}

	/**
	 * SMS events are handled as custom events in the moblet.
	 */
	void customEvent(const MAEvent& event)
	{
		switch (event.type)
		{
			case EVENT_TYPE_SMS:
				// Depending on the event status, we call
				// different JavaScript functions. These are
				// currently hard-coded, but could be passed
				// as parameters to decouple the JavaScript
				// code from the C++ code.
				if (MA_SMS_RESULT_SENT == event.status)
				{
					callJSFunction("SMSSent");
				}
				else if (MA_SMS_RESULT_NOT_SENT == event.status)
				{
					callJSFunction("SMSNotSent");
				}
				else if (MA_SMS_RESULT_DELIVERED == event.status)
				{
					callJSFunction("SMSDelivered");
				}
				else if (MA_SMS_RESULT_NOT_DELIVERED == event.status)
				{
					callJSFunction("SMSNotDelivered");
				}
				break;
		}
	}

	void savePhoneNoAndSendSMS(
		const String& phoneNo,
		const String&  message)
	{
		// Save the phone number.
		savePhoneNo(phoneNo);

		if (sSendSMSForReal)
		{
			// Send the message.
			int result = maSendTextSMS(
				phoneNo.c_str(),
				message.c_str());

			// Provide feedback via JS.
			if (0 != result)
			{
				callJSFunction("SMSNotSent");
			}
		}
		else
		{
			callJSFunction("SMSNotSent");
		}
	}

	/**
	 * Read saved phone number and set it on
	 * the JavaScript side.
	 */
	void setSavedPhoneNo()
	{
		char script[512];
		sprintf(
			script,
			"SetPhoneNo('%s')",
			loadPhoneNo().c_str());
		callJS(script);
	}

	/**
	 * Save the phone number.
	 */
	void savePhoneNo(const String& phoneNo)
	{
		getFileUtil()->writeTextToFile(phoneNoPath(), phoneNo);
	}

	/**
	 * Load the phone number.
	 */
	String loadPhoneNo()
	{
		String phoneNo;
		bool success = getFileUtil()->readTextFromFile(phoneNoPath(), phoneNo);
		if (success)
		{
			return phoneNo;
		}
		else
		{
			return "";
		}
	}

	String phoneNoPath()
	{
		return getFileUtil()->getLocalPath() + "SavedPhoneNo";
	}

	/**
	 * Call a JavaScript function.
	 */
	void callJSFunction(const String& fun)
	{
		char code[512];
		sprintf(code, "%s()", fun.c_str());
		callJS(code);
	}
};
// End of class LoveSMSMoblet

/**
 * Main function that is called when the program starts.
 * This function needs to be declared as extern "C".
 */
extern "C" int MAMain()
{
	Moblet::run(new LoveSMSMoblet());
	return 0;
}