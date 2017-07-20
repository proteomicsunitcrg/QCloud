# QCloud source code

<b>QCrawler</b>: is the entry point into the QCloud system. It performs user authentication, and once the user has defined the acquisition folder and the instrument, it automatically locates and uploads the quality control acquisition files and instrument parameters to the QCloud system through a remote FTP server.

<b>Workflows</b>: supported QC workflows in TOPPAS (OpenMS) format and additional files (FASTA and traML). 

<b>ftp2repo</b>: downloads RAW data from the FTP server @ CRG to the repository/storage. 

<b>mzml2qcml.jar</b>: a Java-based wrapper of OpenMS v2.0. 

<b>qcml2db.jar</b>: wrapper to store qcML data (and other file fomrats like featureXML and idXML) in the persistence layer (MySQL). 



<b>Links to other softwares used in QCloud</b>: 

OpenMS: https://github.com/OpenMS/OpenMS

Proteowizard: https://sourceforge.net/projects/proteowizard/


<b>QCloud is under Creative Commons License ‎Attribution-ShareAlike 4.0.</b>
