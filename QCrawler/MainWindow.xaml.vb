Imports System.IO
Imports System.Net
Imports System.ComponentModel
Imports System.Windows.Forms
Imports System.Drawing
Imports MySql.Data.MySqlClient
Imports System.Data
Imports FASTftp.Utilities.FTP

Class MainWindow

    Private Shared myTimer As New Timers.Timer
    Dim bw As BackgroundWorker = New BackgroundWorker
    Dim flagERR01 As Boolean = False
    Dim uplodadResult As Boolean = False
    Dim stopped As Boolean = False
    Dim localPathToUploadFile As String = ""
    Dim monitoredFolder As String = ""
    Dim FTPaddressString As String = ""
    Dim FTPuserString As String = ""
    Dim FTPpasswordString As String = ""
    Dim FTPfolderString As String = ""
    Dim processedFolderString As String = ""
    Dim QCodeString As String = ""
    Dim QCodeExcludesString As String = ""
    Dim QCextensionString As String = ""
    Private notifyIcon As NotifyIcon
    Dim w As StreamWriter = File.AppendText("log-qcrawler.txt")
    Private Shared BlackListOfFiles As New ArrayList()
    Private Shared notInUseListOfFiles As New ArrayList()
    Dim MinFileSize As Long = 0
    Dim debugMode As Boolean = False
    Dim conn As New MySqlConnection
    Dim dbPHPurl As String = ""
    Dim dbUser As String = ""
    Dim dbPassword As String = ""
    Dim currentInstrument As String = ""
    Dim qcollectorOutputParam As String = ""
    Dim networkErrorMessageCounter As Integer = 0


    Public Sub New()

        InitializeComponent()

        'Splash screen:
        Dim s = New SplashScreen1()
        s.Show()
        System.Threading.Thread.Sleep(2000)
        s.Close()

        bw.WorkerSupportsCancellation = True
        AddHandler myTimer.Elapsed, AddressOf filesManager
        AddHandler bw.DoWork, AddressOf bw_DoWork
        AddHandler bw.RunWorkerCompleted, AddressOf bw_RunWorkerCompleted

        If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Handlers added (filesManager, DoWork And RunWorkerCompleted.")

        'Disable controls: 
        tbMonitoredFolder.IsEnabled = False
        bBrowseAcquisitionFolder.IsEnabled = False
        cbInstruments.IsEnabled = False
        bStartSync.IsEnabled = False
        bStartSync.IsEnabled = False
        bStopSync.IsEnabled = False
        bClearLog.IsEnabled = False
        button.IsEnabled = False

    End Sub

    Private Function checkInternetConn() As Boolean
        Try
            Using client = New WebClient()
                Using stream = client.OpenRead("http://www.google.com")
                    Return True
                End Using
            End Using
        Catch
            Return False
        End Try
    End Function

    Private Function checkNetworkConn() As Boolean
        If Not My.Computer.Network.IsAvailable Then
            Return False
        End If
        Return True
    End Function

    Private Sub checkFolderPermissions(folder As String)
        Dim fileSec = System.IO.File.GetAccessControl(monitoredFolder)
        Dim accessRules = fileSec.GetAccessRules(True, True, GetType(System.Security.Principal.NTAccount))
        For Each rule As System.Security.AccessControl.FileSystemAccessRule In accessRules
            If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Identity Reference: " & rule.IdentityReference.Value)
            If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Access Control Type: " & rule.AccessControlType.ToString())
            If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] File System Rights: " & rule.FileSystemRights.ToString())
            Exit For
        Next
    End Sub

    Function checkRemoteDBavailable() As Boolean
        Dim output As Boolean = False
        Try
            Dim wc As New WebClient
            wc.Headers("content-type") = "application/x-www-form-urlencoded"
            Dim response As String = wc.UploadString(dbPHPurl, "check_conn=true")
            If response IsNot Nothing Then
                output = True
            End If
        Catch ex As Exception
            Console.Write(ex.Message)
        End Try
        Return output
    End Function

    Function checkRemoteDBUserAndPassword(username As String, password As String) As Boolean
        Dim output As Boolean = False
        Try
            Dim wc As New WebClient
            wc.Headers("content-type") = "application/x-www-form-urlencoded"
            Dim response As String = wc.UploadString(dbPHPurl, "username=" & username & "&password=" & password)
            If response.Trim = "True" Then
                output = True
            End If
        Catch ex As Exception
            Console.Write(ex.Message)
        End Try
        Return output
    End Function

    Function checkUserAndPassword(username As String, password As String) As Boolean
        Dim output As Boolean = False
        Try
            Dim wc As New WebClient
            wc.Headers("content-type") = "application/x-www-form-urlencoded"
            Dim response As String = wc.UploadString(dbPHPurl, "username=" & username & "&password=" & password)
            If response.Trim = "True" Then
                output = True
            End If
        Catch ex As Exception
            Console.Write(ex.Message)
        End Try
        Return output
    End Function

    Function getRemoteDBuserInstruments(username As String) As String
        Dim output As String = ""
        Try
            Dim wc As New WebClient
            wc.Headers("content-type") = "application/x-www-form-urlencoded"
            Dim response As String = wc.UploadString(dbPHPurl, "userinstrument=" & username)
            output = response
        Catch ex As Exception
            Console.Write(ex.Message)
        End Try
        Return output
    End Function

    Function getRemoteDBqcodes(username As String, password As String, instrument_name As String) As String
        Dim output As String = ""
        Try
            Dim wc As New WebClient
            wc.Headers("content-type") = "application/x-www-form-urlencoded"
            Dim response As String = wc.UploadString(dbPHPurl, "username=" & username & "&password=" & password & "&instrument_name=" & instrument_name)
            output = response
        Catch ex As Exception
            Console.Write(ex.Message)
        End Try
        Return output
    End Function

    Function getRemoteDBqcollectorParameters(username As String, password As String, instrument_name As String, isqcollector As Boolean) As String
        Dim output As String = ""
        Try
            Dim wc As New WebClient
            wc.Headers("content-type") = "application/x-www-form-urlencoded"
            Dim response As String = wc.UploadString(dbPHPurl, "username=" & username & "&password=" & password & "&instrument_name=" & instrument_name & "&isqcollector=" & isqcollector)
            output = response
        Catch ex As Exception
            Console.Write(ex.Message)
        End Try
        Return output
    End Function

    Private Function getDBversion() As String
        Dim stm As String = "Select VERSION()"
        Dim output As String = ""
        Try
            Dim cmd As MySqlCommand = New MySqlCommand(stm, conn)
            conn.Open()
            output = Convert.ToString(cmd.ExecuteScalar())
            'Dim da As New MySqlDataAdapter
            'Dim ds As New DataSet
            'da.SelectCommand = cmd
            'da.Fill(ds, "user")
            'Console.Write(ds.Tables("user").Rows(0).Item("password").ToString())
        Catch ex As MySqlException
            Console.WriteLine("Error: " & ex.ToString())
        Finally
            conn.Close()
        End Try
        Return output
    End Function

    Private Function getDBpassword(username As String) As String
        Dim stm As String = "SELECT password FROM user WHERE username ='" & username & "'"
        Dim output As String = ""
        Try
            Dim cmd As MySqlCommand = New MySqlCommand(stm, conn)
            conn.Open()
            Dim da As New MySqlDataAdapter
            Dim ds As New DataSet
            da.SelectCommand = cmd
            da.Fill(ds, "user")
            Console.Write(ds.Tables("user").Rows(0).Item("password").ToString())
        Catch ex As MySqlException
            Console.WriteLine("Error: " & ex.ToString())
        Finally
            conn.Close()
        End Try
        Return output
    End Function

    Private Function getSystemDate(localFile As String) As String
        Try
            localFile = localFile.Substring(localFile.LastIndexOf("_") + 1, 14)
            If IsDate(localFile.Substring(0, 4) & " " & localFile.Substring(4, 2) & " " & localFile.Substring(6, 2)) Then
                Return localFile
            Else
                Return ""
            End If
        Catch ex As Exception
            Return ""
        End Try
        Return ""
    End Function

    Private Function getCurrentLogDate() As String
        Dim currentDate As DateTime = DateTime.Now
        Dim currentMonthFolder As String
        currentMonthFolder = Format$(currentDate, "yyyy-MM-dd HH:mm:ss")
        Return "[" & currentMonthFolder & "] "
    End Function

    Private Function getCurrentSystemDate() As String
        Dim currentDate As DateTime = DateTime.Now
        Dim currentMonthFolder As String
        currentMonthFolder = Format$(currentDate, "yyyyMMddhhmmss")
        Return currentMonthFolder
    End Function

    Private Function getCurrentMonthFolder() As String
        Dim currentDate As DateTime = DateTime.Now
        Dim currentMonthFolder As String
        currentMonthFolder = Format$(currentDate, "yyMM")
        Return currentMonthFolder
    End Function

    Private Function IsFileInUse(filename As String) As Boolean
        Dim Locked As Boolean = False
        Try
            'Open the file in a try block in exclusive mode.  
            'If the file is in use, it will throw an IOException. 
            Dim fs As FileStream = File.Open(filename, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None)
            fs.Close()
            ' If an exception is caught, it means that the file is in Use 
        Catch ex As IOException
            Locked = True
        End Try
        Return Locked
    End Function

    Private Function filterLocalFolder(includesListString As String, excludesListString As String, localPath As String) As ArrayList

        'this function is very inefficient but it works for whatever .NET version

        Dim includesList As New ArrayList(includesListString.Split(","))
        Dim excludesList As New ArrayList(excludesListString.Split(","))

        Dim outputList As New ArrayList()
        Dim finalPositiveList As New ArrayList()
        Dim finalNegativeList As New ArrayList()


        Dim dir As New System.IO.DirectoryInfo(localPath)

        For Each include In includesList
            Dim fileListIncludes = dir.GetFiles("*" & include & "*", System.IO.SearchOption.TopDirectoryOnly)
            For Each fileList In fileListIncludes
                finalPositiveList.Add(fileList.FullName)
            Next
        Next

        For Each exclude In excludesList
            Dim fileListExcludes = dir.GetFiles("*" & exclude & "*", System.IO.SearchOption.TopDirectoryOnly)
            For Each fileList In fileListExcludes
                finalNegativeList.Add(fileList.FullName)
            Next
        Next

        For Each finalPositive In finalPositiveList
            Dim flag As Boolean = False
            For Each finalNeagtive In finalNegativeList
                If finalPositive = finalNeagtive Then
                    flag = True
                End If
            Next
            If Not flag Then
                outputList.Add(finalPositive)
            End If
        Next

        Return outputList

    End Function

    Private Function createFTPfolder(ByVal fileUri As String, user As String, password As String, currentMonthFolder As String, qcodes As String) As Boolean
        Dim qcodesList As New ArrayList(qcodes.Split(","))
        Dim myFtpConn As New FTPclient(fileUri, user, password)
        If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] fileuri: " & fileUri & ", user length: " & user.Length & ", password length: " & password.Length)
        Try
            myFtpConn.ListDirectory()
            For Each code In qcodesList
                If Not myFtpConn.FtpDirectoryExists("/" & currentMonthFolder & "/" & code & "/.") Then
                    Try
                        If myFtpConn.FtpCreateDirectory("/" & currentMonthFolder & "/" & code) Then
                            Console.Write(getCurrentLogDate() & "Folder " & fileUri & "/" & currentMonthFolder & "/" & code & " created.")
                            If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "Folder " & fileUri & "/" & currentMonthFolder & "/" & code & " created.")
                        End If
                    Catch ex As Exception
                        If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "Folder " & fileUri & "/" & currentMonthFolder & "/" & code & " cannot be created. Please check.")
                        Log(getCurrentLogDate() & "Folder " & fileUri & "/" & currentMonthFolder & "/" & code & " cannot be created. Please check.", w)
                        Return False
                    End Try
                End If
            Next
            Return True
        Catch ex As Exception
            If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] FTP Server cannot be reached. Please contact QCloud administrator [Detail: ftp address: " & fileUri & "].")
            Log(getCurrentLogDate() & "[ERROR] FTP Server cannot be reached. Please contact QCloud administrator [Detail: ftp address: " & fileUri & "].", w)
            Return False
        End Try
    End Function

    Private Function copyLocalFileToFTP(ByVal fileUri As String, user As String, password As String, localFile As String) As Boolean

        Dim localFileInfo As New FileInfo(localFile)
        Dim localFileName As String = Path.GetFileName(localFile)
        Dim isEverythingOK As Boolean = True

        'Connect to FTP Server: 
        Try
            Dim myFtp As New FTPclient(fileUri, user, password)
            'Compare filenames and size and rename/move when applies:  
            Dim serverFileExist As Boolean = myFtp.FtpFileExists(localFileName)
            If serverFileExist Then
                Dim serverFileSize As Long = myFtp.GetFileSize(localFileName)
                Console.Write(getCurrentLogDate() & "File " & localFileName & " DOES exist in the remote server, so renaming first..." & vbCrLf)
                If serverFileSize = localFileInfo.Length Then 'Compare size between local and remote files
                    renameRepeatedFile(localFile) 'File with same name and same size => REPEATED, so rename as "_rep"
                    stopped = True
                Else
                    localPathToUploadFile = renameModifiedFile(localFile) 'File with same name and different size => MODIFIED, so rename as "_yyyyMMddhhmmss"
                End If
            Else
                Console.Write(getCurrentLogDate() & "File " & localFileName & " DOES NOT exist in the remote server, so uploading now..." & vbCrLf)
                localPathToUploadFile = localFile
            End If
            'Upload file when applies
            If Not stopped Then
                Try
                    Dispatcher.Invoke(Sub()
                                          If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "Uploading file " & Path.GetFileName(localPathToUploadFile) & "...")
                                          Log(getCurrentLogDate() & "Uploading file " & Path.GetFileName(localPathToUploadFile) & "...", w)
                                      End Sub)
                    Console.Write(getCurrentLogDate() & "Uploading file..." & vbCrLf)
                    myFtp.Upload(localPathToUploadFile, Path.GetFileName(localPathToUploadFile)) '<------------------------------UPLOADS FILE TO SERVER'<------------------------------>
                Catch ex As Exception
                    Console.Write(getCurrentLogDate() & "[ERROR] Upload failed! Error description: " & ex.Message & vbCrLf)
                    isEverythingOK = False
                End Try
            End If
        Catch ex As Exception
            Console.Write(getCurrentLogDate() & "[ERROR] FTP server not available. Error description: " & ex.Message & vbCrLf)
            isEverythingOK = False
        End Try

        'If everything went OK: 
        Return isEverythingOK

    End Function

    Private Function moveFileToProcessedFolder(fileToMove As String, processedTargetFilename As String, processedFolder As String) As Boolean
        Try
            If (Not System.IO.Directory.Exists(processedFolder)) Then
                System.IO.Directory.CreateDirectory(processedFolder)
            End If
            If File.Exists(processedTargetFilename) Then
                File.Delete(processedTargetFilename)
            End If
            File.Move(fileToMove, processedTargetFilename)
        Catch ex As Exception
            Console.Write(ex.Message)
            Return False
        End Try
        Return True
    End Function

    Private Sub showRecurrentErrorMessage(message As String, errorCode As String)

        Dim outputMessage As String = ""

        If errorCode Is "ERR01" And Not flagERR01 Then
            outputMessage = message
            Log(outputMessage, w)
            If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, outputMessage)
            flagERR01 = True
        End If

    End Sub

    Private Sub initializeFlags()
        flagERR01 = False
    End Sub

    Private Function getQCodeFromFilename(filename As String) As String

        filename = Path.GetFileName(filename)

        Dim output As String = ""

        If filename.IndexOf("QC") <> -1 Then
            output = filename.Substring(filename.IndexOf("QC") + 0, 4)
        ElseIf filename.Substring(0, 3) = "Cal" Then
            output = "cal"
        End If

        Return output

    End Function

    Private Function getFilesNotInBlackList(fileslist As ArrayList) As ArrayList
        Dim output As New ArrayList()
        For Each file In fileslist
            If BlackListOfFiles.IndexOf(Path.GetFileName(file)) = -1 Then
                output.Add(file)
            End If
        Next
        Return output
    End Function

    Private Function getFilesNotInUse(fileslist As ArrayList) As ArrayList
        notInUseListOfFiles.Clear()
        For Each file In fileslist
            Dim filereader As System.IO.FileInfo = My.Computer.FileSystem.GetFileInfo(file)
            If IsFileInUse(file) Then
                notInUseListOfFiles.Remove(file)
                showRecurrentErrorMessage(getCurrentLogDate() & "[WARNING] The file " & file & " is being used by another program. Skipping it until released.", "ERR01")
            Else
                If filereader.Length > MinFileSize Then ' Check that the file has a minimum size
                    notInUseListOfFiles.Add(file)
                ElseIf filereader.Length <= MinFileSize Then
                    notInUseListOfFiles.Remove(file)
                End If
            End If
        Next
        Return notInUseListOfFiles
    End Function

    Private Function validateQCodes(codes As String) As Boolean
        Dim qcodesList As New ArrayList(codes.Split(","))
        Dim output As Boolean = False
        For Each code In qcodesList
            If code.ToString.Length = 4 Then
                If code.ToString.IndexOf("QC") <> -1 Then
                    output = True
                End If
            Else
                If code.ToString = "cal" Then 'for TTOF
                    output = True
                End If
            End If
        Next
        Return output
    End Function

    Private Function isCalFolder(codes As String) As Boolean
        Dim qcodesList As New ArrayList(codes.Split(","))
        Dim output As Boolean = False
        For Each code In qcodesList
            If code = "cal" Or code = "Cal" Then
                output = True
            End If
        Next
        Return output
    End Function

    Private Sub bw_DoWork(ByVal sender As Object, ByVal e As DoWorkEventArgs)
        Dim worker As BackgroundWorker = CType(sender, BackgroundWorker)
        Dim workerInputs As String() = e.Argument
        uplodadResult = copyLocalFileToFTP(workerInputs(0), workerInputs(1), workerInputs(2), workerInputs(3))
        e.Result = localPathToUploadFile
    End Sub

    Private Sub bw_RunWorkerCompleted(ByVal sender As Object, ByVal e As RunWorkerCompletedEventArgs)
        If Not stopped Then
            If uplodadResult Then
                lbLog.Items.Insert(0, getCurrentLogDate() & "File Uploaded!")
                initializeFlags()
                Log(getCurrentLogDate() & "File Uploaded!", w)
                Dim processedTargetFolder As String = Path.GetDirectoryName(e.Result) & "\" & processedFolderString & "\" & getCurrentMonthFolder()
                    'Move files to processed folder
                    If moveFileToProcessedFolder(Path.GetDirectoryName(e.Result) & "\" & Path.GetFileName(e.Result), processedTargetFolder & "\" & Path.GetFileName(e.Result), processedTargetFolder) Then
                        If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "File " & e.Result & " moved to processed folder")
                        Log(getCurrentLogDate() & "File " & e.Result & " moved to processed folder", w)
                    Else
                        If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] File " & e.Result & " cannot be moved to processed folder. Please check.")
                        Log(getCurrentLogDate() & "[ERROR] File " & e.Result & " cannot be moved to processed folder. Please check.", w)
                    End If
                Else
                    BlackListOfFiles.Add(Path.GetFileName(e.Result))
                If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] Upload failed! Please check this file:  " & Path.GetFileName(e.Result))
                If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Upload failed. Please check this file:  " & Path.GetFileName(e.Result))
            End If
        End If
        myTimer.Start()
    End Sub

    Private Sub bStopSync_Click(sender As Object, e As RoutedEventArgs) Handles bStopSync.Click
        If bw.WorkerSupportsCancellation = True Then
            bw.CancelAsync()
        End If
        bStartSync.IsEnabled = True
        bStopSync.IsEnabled = False
        myTimer.Stop()
        stopped = True
        If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "STOP monitoring RAW files at " & monitoredFolder)
        Log(getCurrentLogDate() & "STOP monitoring RAW files at " & monitoredFolder, w)
    End Sub

    Private Function renameRepeatedFile(localFile As String) As Boolean
        Dim localFileRenameRep As String = Path.GetFileNameWithoutExtension(localFile) & "_rep" & Path.GetExtension(localFile)
        Try
            If Not IsFileInUse(localFile) Then
                My.Computer.FileSystem.RenameFile(localFile, localFileRenameRep)
            End If
        Catch ex As Exception
            Dispatcher.Invoke(Sub()
                                  If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] Renaming failed for the file " & localFile & "Error message:  " & ex.Message)
                                  Log(getCurrentLogDate() & "[ERROR] Renaming failed for the file " & localFile & "Error message: " & ex.Message, w)
                              End Sub)
            Return False
        End Try
        'Move "_rep" file to processed folder
        Dim processedTargetFolder As String = Path.GetDirectoryName(localFile) & "\" & processedFolderString & "\" & getCurrentMonthFolder()
        If moveFileToProcessedFolder(Path.GetDirectoryName(localFile) & "\" & localFileRenameRep, processedTargetFolder & "\" & localFileRenameRep, processedTargetFolder) Then
            Dispatcher.Invoke(Sub()
                                  If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "File " & localFile & " already exist at the FTP Server and with the same file size so it will be only moved to the processed folder and renamed by " & localFileRenameRep)
                                  Log(getCurrentLogDate() & "The file " & localFile & " already exist at the FTP Server and with the same file size so it will be only moved to the processed folder and renamed by " & localFileRenameRep, w)
                              End Sub)
        Else
            Dispatcher.Invoke(Sub()
                                  If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] File " & localFile & " cannot be moved to processed folder. Please check.")
                                  Log(getCurrentLogDate() & "[ERROR] The File " & localFile & " cannot be moved to processed folder. Please check.", w)
                              End Sub)
            Return False
        End If
        Return True
    End Function

    Private Function renameModifiedFile(localFile As String) As String
        Dispatcher.Invoke(Sub()
                              If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "Detected a file in the FTP with the same name but different size, so it's going to be renamed adding a _yyyyMMddhhmmss.")
                              Log(getCurrentLogDate() & "Detected a file in the FTP with the same name but different size, so it's going to be renamed adding a _yyyyMMddhhmmss.", w)
                          End Sub)
        Dim localFileRenameSystemDate As String
        If getSystemDate(localFile) Is "" Then ' INSERT name if it DOES NOT already has an "_yyyyMMddhhmmss"
            Console.Write(getCurrentLogDate() & "Adding _yyyyMMddhhmmss" & vbCrLf)
            localFileRenameSystemDate = Path.GetFileNameWithoutExtension(localFile) & "_" & getCurrentSystemDate() & Path.GetExtension(localFile)
        Else ' UPDATE if it DOES already has an "_yyyyMMddhhmmss"
            Console.Write(getCurrentLogDate() & "It already has _yyyyMMddhhmmss so updating it" & vbCrLf)
            localFileRenameSystemDate = Path.GetFileName(localFile.Substring(0, localFile.LastIndexOf("_")) & "_" & getCurrentSystemDate() & Path.GetExtension(localFile))
        End If
        Try
            If Not IsFileInUse(localFile) Then
                My.Computer.FileSystem.RenameFile(localFile, localFileRenameSystemDate)
            End If
            Console.Write(getCurrentLogDate() & "Local file " & localFile & " renamed to " & localFileRenameSystemDate & vbCrLf)
        Catch ex As Exception
            Console.Write(getCurrentLogDate() & "Exception while renaming the file " & localFile & " to " & localFileRenameSystemDate & ": " & ex.Message & vbCrLf)
        End Try
        ' (note: not moved yet because first it has to be uploaded!) 
        Console.Write(getCurrentLogDate() & "The file " & localFile & " already exist and has the different size so it will be uploaded and renamed by " & localFileRenameSystemDate & vbCrLf)
        Return Path.GetDirectoryName(localFile) & "\" & localFileRenameSystemDate
    End Function

    Private Sub bClearLog_Click(sender As Object, e As RoutedEventArgs) Handles bClearLog.Click
        lbLog.Items.Clear()
    End Sub

    Private Sub bBrowseAcquisitionFolder_Click(sender As Object, e As RoutedEventArgs) Handles bBrowseAcquisitionFolder.Click
        Dim dialog As New FolderBrowserDialog()
        dialog.RootFolder = Environment.SpecialFolder.Desktop
        dialog.SelectedPath = "C:\"
        dialog.Description = "Select Application Configeration Files Path"
        If dialog.ShowDialog() = Windows.Forms.DialogResult.OK Then
            tbMonitoredFolder.Text = dialog.SelectedPath
            tbMonitoredFolder.IsEnabled = False
            bBrowseAcquisitionFolder.IsEnabled = False
            bStartSync.IsEnabled = True
            bStopSync.IsEnabled = True
            bClearLog.IsEnabled = True
            button.IsEnabled = True
            QCodeString = getRemoteDBqcodes(tbUser.Text, tbPassword.Password, cbInstruments.Text).Trim({","c})
            lqcodes.Content = QCodeString
            qcollectorOutputParam = getRemoteDBqcollectorParameters(tbUser.Text, tbPassword.Password, cbInstruments.Text, True)
            Dim params As String() = qcollectorOutputParam.Split(";")
            myTimer.Interval = params(0)
            FTPaddressString = params(1)
            FTPuserString = params(2)
            FTPpasswordString = params(3)
            QCodeExcludesString = params(4)
            QCextensionString = params(5)
            debugMode = params(6)
            MinFileSize = params(7)
        End If
    End Sub

    Private Sub button_Click(sender As Object, e As RoutedEventArgs) Handles button.Click
        Me.WindowState = FormWindowState.Minimized
        If Me.WindowState = FormWindowState.Minimized Then
            notifyIcon.Visible = True
            notifyIcon.Icon = SystemIcons.Application
            notifyIcon.BalloonTipIcon = ToolTipIcon.Info
            notifyIcon.BalloonTipTitle = "QCollector minimized!"
            notifyIcon.BalloonTipText = "Click to maximize again"
            notifyIcon.ShowBalloonTip(50000)
            'Me.Hide()
            ShowInTaskbar = False
        End If
    End Sub

    Private Sub notifyIcon_Click()
        'Me.Show()
        ShowInTaskbar = True
        Me.WindowState = FormWindowState.Normal
        notifyIcon.Visible = False
    End Sub

    Private Sub Log(logMessage As String, w As TextWriter)

        w.WriteLine(logMessage)
        w.Flush()

    End Sub

    Private Sub fillInstrumentsComboBox()
        Dim instruments As String() = getRemoteDBuserInstruments(dbUser).Split(",")
        For Each instrument In instruments
            If instrument IsNot "" Then
                cbInstruments.Items.Add(instrument)
            End If
        Next
    End Sub

    Private Sub cleanListBox()
        If lbLog.Items.Count > 0 Then
            lbLog.SelectedIndex = lbLog.Items.Count - 1
            lbLog.Items.RemoveAt(lbLog.SelectedIndex)
        End If
    End Sub

    Private Sub buttonDBconn_Click(sender As Object, e As RoutedEventArgs) Handles buttonDBconn.Click

        If tbUser.Text IsNot "" Then
            If tbPassword.Password.Length > 0 Then

                dbPHPurl = "http://statsms.crg.es/dbconn.php"
                dbUser = tbUser.Text
                dbPassword = tbPassword.Password

                If (checkRemoteDBavailable()) Then
                    If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] DB connection OK.")
                    If checkRemoteDBUserAndPassword(dbUser, dbPassword) Then
                        If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] User and password OK.")
                        cbInstruments.IsEnabled = True
                        buttonDBconn.IsEnabled = False
                        tbUser.IsEnabled = False
                        tbPassword.IsEnabled = False
                        fillInstrumentsComboBox()
                        If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Instruments combobox filled.")
                    Else
                        If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] Wrong DB user and/or password. Please check.")
                        Log(getCurrentLogDate() & "[ERROR] Wrong DB user and/or password. Please check.", w)
                    End If
                Else
                    If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] DB connection failed.")
                    Log(getCurrentLogDate() & "[ERROR] DB connection failed.", w)
                End If
            Else
                If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] Please, fill in the password.")
                Log(getCurrentLogDate() & "[ERROR] Please, fill in the password.", w)
            End If
        Else
            If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] Please, fill in the user.")
            Log(getCurrentLogDate() & "[ERROR] Please, fill in the user.", w)
        End If

    End Sub

    Private Sub cbInstruments_SelectionChanged(sender As Object, e As SelectionChangedEventArgs) Handles cbInstruments.SelectionChanged
        currentInstrument = cbInstruments.Text
        cbInstruments.IsEnabled = False
        bBrowseAcquisitionFolder.IsEnabled = True
    End Sub

    Private Sub bStartSync_Click(sender As Object, e As RoutedEventArgs) Handles bStartSync.Click

        ' Initialize variables: 
        processedFolderString = "processed"
        monitoredFolder = tbMonitoredFolder.Text
        BlackListOfFiles.Add("")
        bStartSync.IsEnabled = False
        bStopSync.IsEnabled = True

        ' Sets the timer interval (millisec).
        myTimer.Start()

        If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "START monitoring RAW files at " & monitoredFolder)
        Log(getCurrentLogDate() & "START monitoring RAW files at " & monitoredFolder, w)

        If debugMode Then checkFolderPermissions(monitoredFolder)

    End Sub

    Private Sub filesManager(myObject As Object, myEventArgs As EventArgs)
        Dispatcher.Invoke(Sub()
                              If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Checking monitored local folder...")
                              If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] filesManager started.")
                              myTimer.Stop()
                              If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] myTimer stopped.")
                              If checkNetworkConn() Then
                                  networkErrorMessageCounter = 0
                                  If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Network connection OK.")
                                  Dim FTPaddress As String = "ftp://" & FTPaddressString
                                  If IO.Directory.Exists(monitoredFolder) Then 'Check if local folder exists.
                                      If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Monitored folder OK.")
                                      'Check if the "processed" folder exist. If not, create it. 
                                      Dim processedFolderTarget As String = monitoredFolder & "\" & processedFolderString
                                      If IO.Directory.Exists(processedFolderTarget) Then
                                          If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] validateQCodes...")
                                          If validateQCodes(QCodeString) Then

                                              Dim foundFiles As New ArrayList()
                                              If (isCalFolder(QCodeString)) Then 'Append cal files for TTOF
                                                  If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] collecting Cal...")
                                                  If IO.Directory.Exists(monitoredFolder & "\Cal Data") Then
                                                      Dim foundCalFiles As ArrayList = filterLocalFolder(QCodeString, QCodeExcludesString, monitoredFolder & "\Cal Data")
                                                      For Each calfile In foundCalFiles
                                                          foundFiles.Add(calfile)
                                                      Next
                                                  Else
                                                      If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] Cal folder " & monitoredFolder & "\Cal Data" & " not found. Please check.")
                                                      Log(getCurrentLogDate() & "[ERROR] Cal folder " & monitoredFolder & "\Cal Data" & " not found. Please check.", w)
                                                      myTimer.Start()
                                                  End If
                                              Else
                                                  foundFiles = filterLocalFolder(QCodeString, QCodeExcludesString, monitoredFolder) 'Get a list of local files filtered by QC code, extension and not locked.
                                              End If

                                              If foundFiles.Count Then
                                                  If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Found files to process.")
                                                  If createFTPfolder(FTPaddress, FTPuserString, FTPpasswordString, getCurrentMonthFolder(), QCodeString) Then 'Check if the current year-month folder exist. If not, create it:
                                                      Dim notInBlackListFiles As ArrayList = getFilesNotInBlackList(foundFiles)
                                                      Dim cleanFilesList As ArrayList = getFilesNotInUse(notInBlackListFiles)
                                                      If cleanFilesList.Count Then
                                                          stopped = False
                                                          If Not bw.IsBusy = True Then
                                                              If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Running worker to upload the file to FTP.")
                                                              bw.RunWorkerAsync(New String() {FTPaddress & "/" & getCurrentMonthFolder() & "/" & getQCodeFromFilename(cleanFilesList.Item(0)) & "/", FTPuserString, FTPpasswordString, cleanFilesList.Item(0)})
                                                          End If
                                                      Else
                                                          myTimer.Start()
                                                      End If
                                                  Else
                                                      myTimer.Start() 'Folder not created
                                                  End If
                                              Else
                                                  If lbLog.Items.Count >= 15 Then cleanListBox() Else If debugMode Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] No files to process.")
                                                  myTimer.Start() 'No files to process.
                                              End If
                                          Else
                                              If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] QC codes " & QCodeString & " wrong. Please check (e.g. QC1X, QC2X).")
                                              Log(getCurrentLogDate() & "[ERROR] QC codes " & QCodeString & " wrong. Please check (e.g. QC1X, QC2X).", w)
                                              myTimer.Start()
                                          End If
                                      Else
                                          System.IO.Directory.CreateDirectory(processedFolderTarget)
                                          If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & processedFolderTarget & " folder created")
                                          Log(getCurrentLogDate() & processedFolderTarget & " folder created", w)
                                          myTimer.Start()
                                      End If
                                  Else
                                      If lbLog.Items.Count >= 15 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] Local folder " & monitoredFolder & " not found. Please check.")
                                      Log(getCurrentLogDate() & "[ERROR] Local folder " & monitoredFolder & " not found. Please check.", w)
                                      myTimer.Start()
                                  End If
                              Else
                                  If networkErrorMessageCounter <3 Then
                                      If lbLog.Items.Count >= 15 Then cleanListBox() Else If lbLog.Items.Count = 15 Then lbLog.Items.Clear() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] Network connection not available. Please check.")
                                      Log(getCurrentLogDate() & "[ERROR] Network connection not available. Please check.", w)
                                      networkErrorMessageCounter = networkErrorMessageCounter + 1
                                  End If
                                  myTimer.Start()
                              End If
                          End Sub)
    End Sub

End Class