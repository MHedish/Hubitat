# Hubitat Driver for Springs Window Fashions Roller and Sheer Roller Shades

The sheer driver is specifically for the Sheer model of Springs Window Fashions automatic shades.  These shades are different from their roller or double-roller shades in that they are visually closed at non-zero logical level.  They are "closed" at a level of 3 to 6 depending upon the installation and length of the shades.  This driver addresses the difference and restores the logic of a shade being "closed" at a non-zero logical level.

Levels may be set to exact positions (Set Position [0:99]) or as a percentage of "open" (Set Level [1:100]) based on the closed position of the shade as zero.  When "Use relative levels" is set to false, these are the same.

# To Install: 

The drivers are availble via the Hubitat Package Manager or can be installed manually:

1 - Go to your Hubitat Hub.
2 - Click on the hamburger menu in the top left corner and select "Drivers Code."
3 - Click "New Driver" in the top right.
4 - Paste the raw content of the file "springs-window-fashions-sheer-shade.groovy" into the blank area in the middle or click on Import import the URL to the raw content.
5 - Click on "Save" in the top right.
6 - Once the driver is added either connect your shade or, for an existing shade, select the new "Springs Window Fashions Sheer Shade" driver.

Once installed be sure to configure and save your preference settings.
