ControlAndroidDeviceFromPC
==========================

First, I have to apologize for my poor English, if there are any Grammer faults or others, please let me know.


Just as the project name says, this project is aimming at creating a tool for manipulating android devices from pc.

This project is based on android's DDMS code. I just modify some of the code to implement the function of operating the screen of android device. The DDMS lib is a useful tool which provides function such android debug bridge connection and monkey function. So I can capture the screenshot continuously by adb and send touch events and key events to android devices(phones/pads) when the mouse is clicked or slided on the computer side.

Usage
-----

__1.__ Make sure **adb** is installed in your system and can be called from command prompt.

__2.__ Connect your android device to the computer.
* input command `adb devices` to ensure your device is connected.

__3.__ click batch file **run.bat** in the directory **out**. The program should be running normally now.


Supported Operations
--------------------
- Touch, Slide
> Move your mouse upon the screen capture, and press your left mouse button and drag or just a click on an icon.

- Keys
> Menu --> 'M' on the keyboard
> 
> Back --> 'B' on the keyboard
> 
> Home --> 'H' on the keyboard

*You can modify the code to implement your desired operations.*

Contact me
----------
* lrscp(675486378@qq.com)
