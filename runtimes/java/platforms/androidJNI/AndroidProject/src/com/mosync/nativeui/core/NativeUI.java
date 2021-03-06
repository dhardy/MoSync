/* Copyright (C) 2011 MoSync AB

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

package com.mosync.nativeui.core;

import java.util.Hashtable;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.view.View;

import com.mosync.internal.android.MoSyncCameraController;
import com.mosync.internal.android.MoSyncHelpers;
import com.mosync.internal.android.MoSyncThread;
import com.mosync.internal.android.MoSyncFont.MoSyncFontHandle;
import com.mosync.internal.android.MoSyncThread.ImageCache;
import com.mosync.internal.android.MoSyncView;
import com.mosync.internal.generated.IX_WIDGET;
import com.mosync.java.android.MoSync;
import com.mosync.nativeui.ui.factories.CameraPreviewFactory;
import com.mosync.nativeui.ui.factories.ViewFactory;
import com.mosync.nativeui.ui.widgets.ButtonWidget;
import com.mosync.nativeui.ui.widgets.CameraPreviewWidget;
import com.mosync.nativeui.ui.widgets.DialogWidget;
import com.mosync.nativeui.ui.widgets.LabelWidget;
import com.mosync.nativeui.ui.widgets.Layout;
import com.mosync.nativeui.ui.widgets.ListItemWidget;
import com.mosync.nativeui.ui.widgets.MoSyncScreenWidget;
import com.mosync.nativeui.ui.widgets.NavigationBarWidget;
import com.mosync.nativeui.ui.widgets.RadioButtonWidget;
import com.mosync.nativeui.ui.widgets.RadioGroupWidget;
import com.mosync.nativeui.ui.widgets.ScreenWidget;
import com.mosync.nativeui.ui.widgets.StackScreenWidget;
import com.mosync.nativeui.ui.widgets.TabScreenWidget;
import com.mosync.nativeui.ui.widgets.Widget;
import com.mosync.nativeui.util.HandleTable;
import com.mosync.nativeui.util.properties.FeatureNotAvailableException;
import com.mosync.nativeui.util.properties.IntConverter;
import com.mosync.nativeui.util.properties.InvalidPropertyValueException;
import com.mosync.nativeui.util.properties.PropertyConversionException;


/**
 * This class contains the implementation of the NativeUI system calls
 * for Android.
 *
 * @author fmattias
 */
public class NativeUI
{
	/**
	 * Context of the main activity.
	 */
	private Activity m_activity;
	/**
	 * The MoSync thread object.
	 */
	MoSyncThread mMoSyncThread;

	/**
	 * A table that contains a mapping between a handle and a widget, in a
	 * mosync program a handle is the only reference to a widget.
	 */
	private HandleTable<Widget> m_widgetTable = new HandleTable<Widget>();

	/**
	 * Listener for changes in the root view of the screen.
	 */
	private RootViewReplacedListener m_rootViewReplacedListener = null;

	/**
	 * Reference to the last shown screen.
	 */
	private Widget m_currentScreen = null;

	/**
	 * Mapping between image handles and bitmaps.
	 */
	private static Hashtable<Integer, ImageCache> m_imageTable = null;

	/**
	 * Constructor.
	 * @param thread The MoSync thread.
	 * @param activity The Activity in which the widgets should be created.
	 */
	public NativeUI(MoSyncThread thread, Activity activity)
	{
		mMoSyncThread = thread;
		m_activity = activity;
	}

	/**
	 * Returns the bitmap for the given handle.
	 *
	 * @param handle Integer handle for the bitmap to get.
	 * @return the bitmap for the given handle, or null if
	 *         it does not exist.
	 */
	public static Bitmap getBitmap(int handle)
	{
		if( m_imageTable != null && m_imageTable.containsKey( handle ) )
		{
			return m_imageTable.get( handle ).mBitmap;
		}
		else
		{
			return null;
		}
	}

	/**
	 * Sets the bitmap table, that is used to access mosync image
	 * resources.
	 *
	 * @param imageTable The new bitmap table.
	 */
	public static void setImageTable(Hashtable<Integer, ImageCache> imageTable)
	{
		m_imageTable = imageTable;
	}

	/**
	 * Gets the bitmap table, that can be modified.
	 *
	 * @return The bitmap table.
	 */
	public Hashtable<Integer, ImageCache> getImageTable()
	{
		return m_imageTable;
	}

	public HandleTable<Widget> getWidgetTable()
	{
		return m_widgetTable;
	}

	/**
	 * Sets the default MoSync canvas view, so that it is possible
	 * to switch back to it from native UI.
	 *
	 * @param mosyncScreen The MoSync canvas view.
	 */
	public void setMoSyncScreen(MoSyncView mosyncScreen)
	{
		if( mosyncScreen != null )
		{
			MoSyncScreenWidget screenWidget = new MoSyncScreenWidget(
					IX_WIDGET.MAW_CONSTANT_MOSYNC_SCREEN_HANDLE,
					mosyncScreen );

			m_widgetTable.add( IX_WIDGET.MAW_CONSTANT_MOSYNC_SCREEN_HANDLE,
					screenWidget );
		}
		else
		{
			// If there is no MoSyncView, we cannot provide one here
			m_widgetTable.remove( IX_WIDGET.MAW_CONSTANT_MOSYNC_SCREEN_HANDLE );
		}
	}

	/**
	 * Internal function for the maWidgetCreate system call.
	 * It uses the ViewFactory to create a widget of the
	 * given type, puts it in the handle table and returns it.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetCreate(String type)
	{
		if( !ViewFactory.typeExists( type ) )
		{
			Log.e( "MoSync", "maWidgetCreate: Unknown type: " + type );
			return IX_WIDGET.MAW_RES_INVALID_TYPE_NAME;
		}

		int nextHandle = m_widgetTable.getNextHandle( );
		Widget widget = ViewFactory.createView( type, m_activity, nextHandle );

		if( widget != null )
		{
			m_widgetTable.add( nextHandle, widget );
			return nextHandle;
		}
		else
		{
			Log.e("MoSync", "maWidgetCreate: Error while creating widget: " + type );
			return IX_WIDGET.MAW_RES_ERROR;
		}
	}

	/**
	 * Internal function for the maWidgetDestroy system call.
	 * Destroys the given widget handle and all of its
	 * children.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetDestroy(int widgetHandle)
	{
		Widget widget = m_widgetTable.get( widgetHandle );
		if( widget == null )
		{
			Log.e( "MoSync", "maWidgetDestroy: Invalid widget handle: " + widgetHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		internalMaWidgetDestroy( widget );

		return IX_WIDGET.MAW_RES_OK;
	}

	/**
	 * Recursive function that destroys the given widget and
	 * its children.
	 *
	 * @param widgetToDestroy The widget to destroy.
	 */
	private void internalMaWidgetDestroy(Widget widgetToDestroy)
	{
		// Disconnect widget from widget tree
		Layout parent = (Layout) widgetToDestroy.getParent( );
		if( parent != null )
		{
			parent.removeChild( widgetToDestroy );
		}

		// Disconnect and destroy children
		if( widgetToDestroy.isLayout( ) )
		{
			Layout widgetToDestroyAsLayout = (Layout) widgetToDestroy;
			for(Widget child : widgetToDestroyAsLayout.getChildren( ))
			{
				internalMaWidgetDestroy( child );
			}
		}

		// Destroy widget
		m_widgetTable.remove( widgetToDestroy.getHandle( ) );
	}

	/**
	 * Internal function for the maWidgetAdd system call.
	 * Uses maWidgetInsertChild to add the element at the
	 * end.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetAdd(int parentHandle, int childHandle)
	{
		return maWidgetInsertChild(parentHandle, childHandle, -1);
	}

	/**
	 * Internal function for the maWidgetInsertChild system call.
	 * Inserts a child at a specific position in the given parent, the parent
	 * must be of type Layout.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetInsertChild(int parentHandle, int childHandle, int index)
	{
		if( parentHandle == childHandle )
		{
			Log.e( "MoSync", "maWidgetInsertChild: Child and parent are the same." );
			return IX_WIDGET.MAW_RES_ERROR;
		}

		Widget parent = m_widgetTable.get( parentHandle );
		Widget child = m_widgetTable.get( childHandle );

		if( child == null )
		{
			Log.e( "MoSync", "maWidgetInsertChild: Invalid child widget handle: " + childHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}
		if( parent == null )
		{
			Log.e( "MoSync", "maWidgetInsertChild: Invalid parent widget handle: " + parentHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}
		if ( child.getParent() != null )
		{
			Log.e( "MoSync", "maWidgetInsertChild: Child already has a parent." );
			return IX_WIDGET.MAW_RES_ERROR;
		}
		if( index < -1 )
		{
			Log.e( "MoSync", "maWidgetInsertChild: Invalid index: " + index );
			return IX_WIDGET.MAW_RES_INVALID_INDEX;
		}
		if ( child.isDialog() )
		{
			Log.e( "MoSync", "maWidgetInsertChild: Cannot add a dialog to a widget. " );
			return IX_WIDGET.MAW_RES_CANNOT_INSERT_DIALOG;
		}
		if ( child instanceof RadioButtonWidget )
		{
			Log.e( "MoSync", "maWidgetInsertChild: Cannot add a radio button to a layout, only to Radio Groups." );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		if ( parent.isDialog() )
		{
			DialogWidget parentAsDialog = (DialogWidget) parent;
			parentAsDialog.addChildAt(child, index);
		}
		else if ( parent.isLayout() )
		{
			Layout parentAsLayout = (Layout) parent;
			parentAsLayout.addChildAt( child, index );
		}
		else
		{
			Log.e( "MoSync", "maWidgetInsertChild: Parent " + parentHandle + " is not a layout or a dialog." );
			return IX_WIDGET.MAW_RES_INVALID_LAYOUT;
		}
		return IX_WIDGET.MAW_RES_OK;
	}

	/**
	 * Internal function for the maWidgetRemove system call.
	 * Removes a child from its parent, but keeps a
	 * reference to it.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetRemove(int childHandle)
	{
		Widget child = m_widgetTable.get( childHandle );
		if( child == null )
		{
			Log.e( "MoSync", "maWidgetRemove: Invalid child widget handle: " + childHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		Widget parent = child.getParent( );
		if( parent == null )
		{
			Log.e( "MoSync", "maWidgetRemove: Widget " + childHandle + " has no parent." );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		if ( parent.isDialog() )
		{
			DialogWidget parentAsDialog = (DialogWidget) parent;
			parentAsDialog.removeChild(child);
		}
		else if ( parent.isLayout() )
		{
			Layout parentAsLayout = (Layout) parent;
			parentAsLayout.removeChild( child );
		}
		else
		{
			Log.e( "MoSync", "maWidgetRemove: Parent for " + childHandle + " is not a layout or a dialog." );
			return IX_WIDGET.MAW_RES_INVALID_LAYOUT;
		}

		return IX_WIDGET.MAW_RES_OK;
	}

	/**
	 * Internal function for the maWidgetDialogShow system call.
	 * It displays the given dialog.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetDialogShow(int dialogHandle)
	{
		Widget parent = m_widgetTable.get( dialogHandle );
		if( parent == null )
		{
			Log.e( "MoSync", "maWidgetDialogShow: Invalid dialog widget handle: " + dialogHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		if ( !(parent instanceof DialogWidget) )
		{
			Log.e( "MoSync", "maWidgetScreenShow: Widget is not a dialog: " + dialogHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		DialogWidget dialog = (DialogWidget) parent;
		dialog.show();

		return IX_WIDGET.MAW_RES_OK;
	}

	/**
	 * Internal function for the maWidgetCreate system call.
	 * It uses the ViewFactory to create a widget of the
	 * given type, puts it in the handle table and returns it.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetDialogHide(int dialogHandle)
	{
		Widget parent = m_widgetTable.get( dialogHandle );
		if( parent == null )
		{
			Log.e( "MoSync", "maWidgetDialogShow: Invalid dialog widget handle: " + dialogHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		if ( !(parent instanceof DialogWidget) )
		{
			Log.e( "MoSync", "maWidgetScreenShow: Widget is not a dialog: " + dialogHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		DialogWidget dialog = (DialogWidget) parent;
		dialog.hide();

		return IX_WIDGET.MAW_RES_OK;
	}

	/**
	 * Internal function for the maWidgetScreenShow system call.
	 * Sets the root widget to the root of the given screen, but
	 * it does not actually call setContentView, this should
	 * be done through the RootViewReplacedListener.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetScreenShow(int screenWidget)
	{
		Widget screen = m_widgetTable.get( screenWidget );
		if( screen == null )
		{
			Log.e( "MoSync", "maWidgetScreenShow: Invalid screen handle: " + screenWidget );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}
		if( !( screen instanceof ScreenWidget ) && !( screen instanceof MoSyncScreenWidget ) )
		{
			Log.e( "MoSync", "maWidgetScreenShow: Widget is not a screen: " + screenWidget );
			return IX_WIDGET.MAW_RES_INVALID_SCREEN;
		}

		if( m_rootViewReplacedListener != null )
		{
			//m_rootViewReplacedListener.rootViewReplaced( screen.getView( ) );
			m_rootViewReplacedListener.rootViewReplaced( screen.getRootView( ) );
		}
		m_currentScreen = screen;

		return IX_WIDGET.MAW_RES_OK;
	}

	/**
	 * Internal function for the maWidgetStackScreenPush system call.
	 * Takes out the stack screen from the widget table and pushes
	 * the screen to it.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetStackScreenPush(int stackScreenHandle, int newScreenHandle)
	{

		Widget stackScreenWidget = m_widgetTable.get( stackScreenHandle );
		if( stackScreenWidget == null )
		{
			Log.e( "MoSync", "maWidgetStackScreenPush: Invalid stack screen handle: " + stackScreenHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}
		if( !(stackScreenWidget instanceof StackScreenWidget) )
		{
			Log.e( "MoSync", "maWidgetStackScreenPush: Widget is not a stack screen: " + stackScreenHandle );
			return IX_WIDGET.MAW_RES_ERROR;
		}

		Widget newScreenWidget = m_widgetTable.get( newScreenHandle );
		if( newScreenWidget == null )
		{
			Log.e( "MoSync", "maWidgetStackScreenPush: Invalid screen handle: " + newScreenHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}
		if( !(newScreenWidget instanceof ScreenWidget) )
		{
			Log.e( "MoSync", "maWidgetStackScreenPush: Widget is not a screen: " + newScreenHandle );
			return IX_WIDGET.MAW_RES_ERROR;
		}

		StackScreenWidget stackScreen = (StackScreenWidget) stackScreenWidget;
		ScreenWidget newScreen = (ScreenWidget) newScreenWidget;

		stackScreen.push( newScreen );

		// This is the currently shown ScreenWidget.
		m_currentScreen = stackScreen;

		return IX_WIDGET.MAW_RES_OK;
	}

	/**
	 * Internal function for the maWidgetStackScreenPop system call.
	 * Takes out the stack screen from the widget table and pops the
	 * current screen from it.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetStackScreenPop(int stackScreenHandle)
	{
		Widget stackScreenWidget = m_widgetTable.get( stackScreenHandle );
		if( stackScreenWidget == null )
		{
			Log.e( "MoSync", "maWidgetStackScreenPop: Invalid stack screen handle: " + stackScreenHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}
		if( !(stackScreenWidget instanceof StackScreenWidget) )
		{
			Log.e( "MoSync", "maWidgetStackScreenPop: Widget is not a stack screen: " + stackScreenHandle );
			return IX_WIDGET.MAW_RES_ERROR;
		}

		StackScreenWidget stackScreen = (StackScreenWidget) stackScreenWidget;
		stackScreen.pop( );

		return IX_WIDGET.MAW_RES_OK;
	}

	/**
	 * Set the current screen.
	 * @param screenHandle The visible screen.
	 */
	public void setCurrentScreen(int screenHandle)
	{
		Widget widget = m_widgetTable.get( screenHandle );
		ScreenWidget screen = (ScreenWidget) widget;
		m_currentScreen = screen;
	}

	/**
	 * Get the current visible screen.
	 * @return the screen widget.
	 */
	public ScreenWidget getCurrentScreen()
	{
		if ( m_currentScreen instanceof StackScreenWidget )
		{
			return ((StackScreenWidget)  m_currentScreen).getCurrentScreen();
		}
		else if ( m_currentScreen instanceof TabScreenWidget )
		{
			ScreenWidget screen = ((TabScreenWidget) m_currentScreen).getCurrentTabScreen();
			if ( screen instanceof StackScreenWidget )
			{
				return ((StackScreenWidget) screen).getCurrentScreen();
			}
			return screen;
		}

		return (ScreenWidget) m_currentScreen;
	}

	/**
	 * Internal function for the maWidgetSetProperty system call.
	 * Sets a property on the given widget, by accessing it from
	 * the widget table and calling its setProperty method.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetSetProperty(int widgetHandle, String key, String value)
	{
		Widget widget = m_widgetTable.get( widgetHandle );
		if( widget == null )
		{
			Log.e( "MoSync", "maWidgetSetProperty: Invalid child widget handle: " + widgetHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		boolean result;

		// Set font, if available on the current widget.
		if ( key.equals( IX_WIDGET.MAW_LABEL_FONT_HANDLE ) )
		{
			return setWidgetFont(widget, key, value);
		}

		try
		{
			if ( widget instanceof RadioGroupWidget && key.equals(IX_WIDGET.MAW_RADIO_GROUP_ADD_VIEW) )
			{
				int radioButtonHandle = IntConverter.convert(value);
				Widget child = m_widgetTable.get( radioButtonHandle );
				if ( child != null && child instanceof RadioButtonWidget )
				{
					RadioButtonWidget radioButton = (RadioButtonWidget) child;
					radioButton.setId(radioButtonHandle);
					RadioGroupWidget radioGroup = (RadioGroupWidget) widget;
					radioGroup.addButton( radioButton );

					return IX_WIDGET.MAW_RES_OK;
				}
				else
				{
					Log.e( "MoSync", "Error while converting property value '" + value + ". Value needs to be a valid radio button handle" );
					return IX_WIDGET.MAW_RES_INVALID_PROPERTY_VALUE;
				}
			}
			if ( widget instanceof RadioGroupWidget && key.equals(IX_WIDGET.MAW_RADIO_GROUP_SELECTED) )
			{
				int radioButtonHandle = IntConverter.convert(value);
				Widget child = m_widgetTable.get( radioButtonHandle );
				RadioGroupWidget radioGroup = (RadioGroupWidget) widget;
				if ( radioButtonHandle == -1 )
				{
					// Setting -1 as the selection identifier clears the selection.
					radioGroup.checkRadioButton(-1);
				}
				else if ( child != null && child instanceof RadioButtonWidget )
				{
					RadioButtonWidget radioButton = (RadioButtonWidget) child;
					radioGroup.checkRadioButton( radioButton.getId() );
				}
				else
				{
					Log.e( "MoSync", "Error while converting property value '" + value + ". Value needs to be a valid radio button handle" );
					return IX_WIDGET.MAW_RES_INVALID_PROPERTY_VALUE;
				}

				return IX_WIDGET.MAW_RES_OK;
			}

			result =  widget.setProperty( key, value );
		}
		catch(PropertyConversionException pce)
		{
			Log.e( "MoSync", "Error while converting property value '" + value + "': " + pce.getMessage( ) );
			return IX_WIDGET.MAW_RES_INVALID_PROPERTY_VALUE;
		}
		catch(InvalidPropertyValueException ipve)
		{
			Log.e( "MoSync", "Error while setting property: " + ipve.getMessage( ) );
			return IX_WIDGET.MAW_RES_INVALID_PROPERTY_VALUE;
		}
		catch(FeatureNotAvailableException fnae)
		{
			Log.e("MoSync", "Feature not available exception: " + fnae.getMessage() );
			return IX_WIDGET.MAW_RES_FEATURE_NOT_AVAILABLE;
		}

		if( result )
		{
			return IX_WIDGET.MAW_RES_OK;
		}
		else
		{
			Log.e( "MoSync", "maWidgetSetProperty: Invalid property '" + key + "' on widget: " + widgetHandle );
			return IX_WIDGET.MAW_RES_INVALID_PROPERTY_NAME;
		}
	}

	/**
	 * Internal function for the maWidgetGetProperty system call.
	 * Gets a property on the given widget, by accessing it from
	 * the widget table and calling its getProperty method.
	 *
	 * Note: Should only be called on the UI thread.
	 */
	public int maWidgetGetProperty(
		int widgetHandle,
		String key,
		int memBuffer,
		int memBufferSize)
	{
		Widget widget = m_widgetTable.get( widgetHandle );
		if( widget == null )
		{
			Log.e( "MoSync", "maWidgetGetProperty: Invalid child widget handle: " + widgetHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		String result;
		try {
			if ( widget instanceof RadioGroupWidget && key.equals(IX_WIDGET.MAW_RADIO_GROUP_SELECTED) )
			{
				RadioGroupWidget radioGroup = (RadioGroupWidget) widget;
				RadioButtonWidget selectedButton = radioGroup.getButton(radioGroup.getChecked());
				if ( selectedButton == null )
				{
					result = "-1";
				}
				else
				{
					result = Integer.toString( selectedButton.getHandle() );
				}
			}
			else
			{
				result = widget.getProperty( key );
			}
		}catch( FeatureNotAvailableException fnae)
		{
			Log.e("MoSync", "Feature not available exception: " + fnae.getMessage() );
			return IX_WIDGET.MAW_RES_FEATURE_NOT_AVAILABLE;
		}

		if( result.equals(Widget.INVALID_PROPERTY_NAME) )
		{
			Log.e( "MoSync", "maWidgetGetProperty: Invalid or empty property '" +
					key + "' on widget: " + widgetHandle );
			return IX_WIDGET.MAW_RES_INVALID_PROPERTY_NAME;
		}

		if( result.length( ) + 1 > memBufferSize )
		{
			Log.e( "MoSync", "maWidgetGetProperty: Buffer size " + memBufferSize +
					" too short to hold buffer of size: " + result.length( ) + 1 );
			return IX_WIDGET.MAW_RES_INVALID_STRING_BUFFER_SIZE;
		}

		byte[] ba = result.getBytes();

		// Write string to MoSync memory.
		MoSyncThread mosyncThread = ((MoSync) m_activity).getMoSyncThread( );
		mosyncThread.mMemDataSection.position( memBuffer );
		mosyncThread.mMemDataSection.put( ba );
		mosyncThread.mMemDataSection.put( (byte)0 );

		return result.length( );
	}

	/**
	 * Add an item to the Options Menu associated to a screen.
	 * @param widgetHandle The screen handle.
	 * @param title The title associated for the new item. Can be left null.
	 * @param iconHandle MoSync handle to an uncompressed image resource,or:
	 * a predefined Android icon.
	 * @param iconPredefined Specifies if the icon is a project resource, or one of
	 * the predefined Android icons. By default it's value is 0.
	 * @return The index on which the menu item was added in the options menu,
	 * or an error code otherwise.
	 */
	public int maWidgetScreenAddOptionsMenuItem(
			final int widgetHandle,
			final String title,
			final int iconHandle,
			final int iconPredefined)
	{
		Widget widget = m_widgetTable.get( widgetHandle );
		if( widget == null || !(widget instanceof ScreenWidget) )
		{
			Log.e( "@@MoSync", "maWidgetScreenAddOptionsMenuItem: Invalid screen widget handle: " + widgetHandle );
			return IX_WIDGET.MAW_RES_INVALID_HANDLE;
		}

		ScreenWidget screen = (ScreenWidget) widget;

		switch(iconPredefined){
		case 1:
		{
			if ( iconHandle >= IX_WIDGET.MAW_OPTIONS_MENU_ICON_CONSTANT_ADD &&
					iconHandle <= IX_WIDGET.MAW_OPTIONS_MENU_ICON_CONSTANT_ZOOM )
			return screen.addMenuItem(title, iconHandle);
			else
			{
				Log.e("@@MoSync","maWidgetScreenAddOptionsMenuItem: Invalid icon resource ID: " + iconHandle);
				return IX_WIDGET.MAW_RES_ERROR;
			}
		}
		case 0:
		{
			if ( iconHandle >= 0 && m_imageTable.containsKey( iconHandle ) )
			{
				Bitmap icon = NativeUI.getBitmap( iconHandle );
				if( icon != null )
				{
					// When adding a new menu item the id is returned.
					return screen.addMenuItem(title, new BitmapDrawable(icon));
				}
				else
				{
					Log.e("@@MoSync","maWidgetScreenAddOptionsMenuItem: Invalid icon handle: " + iconHandle);
					return IX_WIDGET.MAW_RES_ERROR;
				}
			}
			else
			{
				return screen.addMenuItem(title, null);
			}
		}
		default:
		{
			Log.e( "@@MoSync", "maWidgetScreenAddOptionsMenuItem: Invalid iconPredefined value: " + iconPredefined );
			return IX_WIDGET.MAW_RES_ERROR;
		}
		}
	}

	/**
	 * Called when the back button has been pressed.
	 */
	public void handleBack()
	{
		if( m_currentScreen != null )
		{
			m_currentScreen.handleBack( );
		}
	}

	/**
	 * Sets a listener for when the root view is changed.
	 *
	 * @param rootViewReplacedListener The class that listens for changes.
	 */
	public void setRootViewReplacedListener(RootViewReplacedListener rootViewReplacedListener)
	{
		m_rootViewReplacedListener = rootViewReplacedListener;
	}

	public interface RootViewReplacedListener
	{
		/**
		 * Called when the root view has been replaced
		 * by another root view.
		 *
		 * @param newRoot The new root view.
		 */
		void rootViewReplaced(View newRoot);
	}

	public Widget getCameraView(final int handle)
	{
		return m_widgetTable.get(handle);
	}

	public Widget getWidget(final int handle)
	{
		return (Widget) m_widgetTable.get( handle );
	}

	/**
	 * Set the font property to the
	 *
	 * @param property The property name.
	 * @param value The property value.
	 * @return error code or MAW_RES_OK.
	 */
	public int setWidgetFont(Widget widget, final String property,
			final String value)
	{
		// Set the typeface to the label, button widget, edit Box or list view item.
		MoSyncFontHandle currentFont = null;

		// Search the handle in the list of fonts.
		try {
			currentFont = mMoSyncThread.getMoSyncFont(IntConverter .convert(value));
		} catch (PropertyConversionException pce)
		{
			Log.e("MoSync", "Error while getting font handle with value '"
					+ value + "Invalid property value");
			return IX_WIDGET.MAW_RES_INVALID_PROPERTY_VALUE;
		}

		if (currentFont == null)
		{
			Log.e("MoSync", "Error while getting font handle with value '"
					+ value + " The handle was not found");
			return IX_WIDGET.MAW_RES_INVALID_PROPERTY_VALUE;
		}
		else
		{
			Log.e("@@MoSync", "Set font typeface to native ui widget");
			boolean fontWasSet = widget.setFontTypeface(
					currentFont.getTypeface(), currentFont.getFontSize());
			if (!fontWasSet)
			{
				Log.e("MoSync", "Error while setting property '" + property);
				return IX_WIDGET.MAW_RES_INVALID_PROPERTY_NAME;
			}
			return IX_WIDGET.MAW_RES_OK;
		}
	}
}
