# QCloud source code

<b>mzml2qcml.jar</b>: a Java-based wrapper of OpenMS v2.0. 

<b>qcml2db.jar</b>: wrapper to store qcML data (and other file fomrats like featureXML and idXML) in the persistence layer (MySQL). 

<b>QCrawler</b>: is the entry point into the QCloud system. It performs user authentication, and once the user has defined the acquisition folder and the instrument, it automatically locates and uploads the quality control acquisition files and instrument parameters to the QCloud system through a remote FTP server.
