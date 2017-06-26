# QCloud source code

QCrawler: a thin client (374 KB) that needs to be run in the instrument computer. QCrawler is the entry point into the QCloud system. It performs user authentication, and once the user has defined the acquisition folder and the instrument, it automatically locates and uploads the quality control acquisition files and instrument parameters to the QCloud system through a remote FTP server. QCrawler is coded with the .NET v3.0 framework to be retro-compatible with a wide range of versions of the Microsoft WindowsTM operating system installed in the acquisition computers.

mzml2qcml.jar: a Java-based wrapper of OpenMS v2.0 

qcml2db.jar: wrapper to store qcML data (and other file fomrats like featureXML and idXML) in the persistence layer (MySQL). 
