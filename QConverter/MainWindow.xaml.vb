Imports System.IO
Imports System.ComponentModel
Imports System.Windows.Forms
Imports System.Drawing
Imports System.Linq

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
    Dim w As StreamWriter = File.AppendText("log-qconverter.txt")
    Dim rootRawFoldersList As New ArrayList
    Dim flagCorruptedFile = False
    Dim counterMsconvert As Integer = 0
    Dim msconvertMaxTrials As Integer = 25
    Dim debugRefreshInterval = 5000
    Dim productionRefreshInterval = 150000
    Dim counterProcessFiles As Integer
    Dim maxProcessFiles As Integer = 5

    Public Sub New()

        InitializeComponent()
        bw.WorkerSupportsCancellation = True
        AddHandler myTimer.Elapsed, AddressOf filesManager

        'notifyIcon = New NotifyIcon()
        'Try
        'notifyIcon.Icon = New Icon("QCicon.ico")
        'Catch ex As Exception
        'Console.Write("Error in System tray icon.")
        'End Try
        'AddHandler notifyIcon.Click, AddressOf notifyIcon_Click

    End Sub

    Private Function checkNetworkConn() As Boolean
        If Not My.Computer.Network.IsAvailable Then
            Return False
        End If
        Return True
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

    Private Sub initializeFlags()
        flagERR01 = False
    End Sub

    Private Function compareSourceAndMzmlFolders(inputraw As String, inputmzml As String) As IEnumerable(Of String)

        Dim flagRawFolder As Boolean = False
        Dim flagMzmlFolder As Boolean = False
        Dim flagIsWiff As Boolean = False

        Dim listrawwoext As New List(Of String)
        Dim listmzmlwoext As New List(Of String)

        Dim listExceptFiles As IEnumerable(Of String) = Enumerable.Empty(Of String)
        Dim listExceptFilesWithExtension As New List(Of String)
        Dim listIntersectFiles As IEnumerable(Of String) = Enumerable.Empty(Of String)
        Dim listDistinctFiles As IEnumerable(Of String) = Enumerable.Empty(Of String)

        If Directory.Exists(inputraw) Then
            flagRawFolder = True
        End If

        If Directory.Exists(inputmzml) Then
            flagMzmlFolder = True
        End If

        If flagRawFolder And flagMzmlFolder Then

            Dim qcode As String

            If inputraw.Substring(inputraw.Length - 4, 3) = "cal" Then
                qcode = "cal"
            Else
                qcode = inputraw.Substring(inputraw.Length - 5, 4)
            End If

            ' Take a snapshot of the file system. 
            Dim dirraw As New System.IO.DirectoryInfo(inputraw)
            Dim dirmzml As New System.IO.DirectoryInfo(inputmzml)

            'List all files in each directory
            If inputraw.IndexOf("\Wiff\") <> -1 Or inputraw.IndexOf("\wiff\") <> -1 Then
                flagIsWiff = True
            End If

            Dim listraw As FileInfo()
            Dim listmzml As FileInfo()

            If flagIsWiff Then
                listraw = dirraw.GetFiles("*" & qcode & "*.wiff", System.IO.SearchOption.TopDirectoryOnly)
                listmzml = dirmzml.GetFiles("*" & qcode & "*", System.IO.SearchOption.TopDirectoryOnly)
            Else
                listraw = dirraw.GetFiles("*" & qcode & "*", System.IO.SearchOption.TopDirectoryOnly)
                listmzml = dirmzml.GetFiles("*" & qcode & "*", System.IO.SearchOption.TopDirectoryOnly)
            End If

            ' Remove files with size = 0 and extensions
            For Each rawfile In listraw
                If flagIsWiff Then
                    If File.Exists(inputraw & rawfile.ToString & ".scan") Then
                        Dim scanfile As FileInfo = My.Computer.FileSystem.GetFileInfo(inputraw & rawfile.ToString & ".scan")
                        If rawfile.Length <> 0 And scanfile.Length <> 0 Then
                            listrawwoext.Add(rawfile.ToString().Substring(0, rawfile.ToString().LastIndexOf(".")))
                        End If
                    Else
                        If rawfile.Length <> 0 Then
                            listrawwoext.Add(rawfile.ToString().Substring(0, rawfile.ToString().LastIndexOf(".")))
                        End If
                    End If
                Else
                    If rawfile.Length <> 0 Then
                        listrawwoext.Add(rawfile.ToString().Substring(0, rawfile.ToString().LastIndexOf(".")))
                    End If
                End If
            Next

            ' Remove extensions
            'For Each rawfile In listraw
            'listrawwoext.Add(rawfile.ToString().Substring(0, rawfile.ToString().LastIndexOf(".")))
            'Next

            For Each mzmlfile In listmzml
                listmzmlwoext.Add(mzmlfile.ToString().Substring(0, mzmlfile.ToString().LastIndexOf(".")))
            Next

            ' Find the set difference between the two folders.
            listExceptFiles = listrawwoext.Except(listmzmlwoext)

            ' Re-add extensions
            For Each outputfile In listExceptFiles
                If flagIsWiff Then
                    listExceptFilesWithExtension.Add(outputfile.ToString & ".wiff")
                Else
                    listExceptFilesWithExtension.Add(outputfile.ToString & ".raw")
                End If
            Next

        End If

        Return listExceptFilesWithExtension

    End Function

    Private Function manageCorruptedFile(rawFile As String)
        If File.Exists(rawFile) Then
            Try
                If lbLog.Items.Count >= 35 Then cleanListBox() Else If cbDebugMode.IsChecked Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] File corrupted. Renaming...")
                My.Computer.FileSystem.RenameFile(rawFile, Path.GetFileNameWithoutExtension(rawFile) & "_CORRUPTED" & Path.GetExtension(rawFile))
                If lbLog.Items.Count >= 35 Then cleanListBox() Else If cbDebugMode.IsChecked Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Renaming ended properly!")
                flagCorruptedFile = True
            Catch ex As IOException
                If lbLog.Items.Count >= 35 Then cleanListBox() Else If cbDebugMode.IsChecked Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Renaming IOException: " & ex.Message)
                Console.Write(ex.Message)
                If ex.Message.IndexOf("file already exists") <> -1 Then
                    Try
                        My.Computer.FileSystem.DeleteFile(rawFile)
                        If lbLog.Items.Count >= 35 Then cleanListBox() Else If cbDebugMode.IsChecked Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] File deleted.")
                    Catch exdelete As Exception
                        If lbLog.Items.Count >= 35 Then cleanListBox() Else If cbDebugMode.IsChecked Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] The file cannot be deleted. Check permissions.")
                    End Try
                End If
            Catch exgen As Exception
                If lbLog.Items.Count >= 35 Then cleanListBox() Else If cbDebugMode.IsChecked Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Renaming general Esxception: " & exgen.Message)
                Console.Write(exgen.Message)
            End Try
        End If
    End Function

    Private Function isFileCorrupted(rawFile) As Boolean

        Dim bReturn As Boolean = False
        If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[WARNING] Checking file integrity with msaccess.")
        Log(getCurrentLogDate() & "[WARNING] Checking file integrity with msaccess.", w)
        Dim process As New Process()
        process.StartInfo.FileName = "msaccess.exe"
        process.StartInfo.Arguments = " -x run_summary " & rawFile
        process.StartInfo.RedirectStandardOutput = True
        process.StartInfo.WindowStyle = ProcessWindowStyle.Hidden
        process.StartInfo.UseShellExecute = False
        Dim listFiles As System.Diagnostics.Process
        Console.Write(process.StartInfo.FileName)
        listFiles = System.Diagnostics.Process.Start(process.StartInfo)
        Dim myOutput As System.IO.StreamReader = listFiles.StandardOutput
        listFiles.WaitForExit()

        If listFiles.HasExited Then
            Dim output As String = myOutput.ReadToEnd
            If output.IndexOf("MS1") = -1 Then
                If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[WARNING] Empty output from msaccess. Trying again...")
                Log(getCurrentLogDate() & "[WARNING] Empty output from msaccess. Trying again...", w)
                bReturn = True
            Else
                If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[WARNING] Empty output from msaccess. Trying again...")
                Log(getCurrentLogDate() & "[WARNING] Empty output from msaccess. Trying again...", w)
                bReturn = False
            End If
        End If

        Return bReturn

    End Function

    Private Sub runMzMLconvertProcess(rawFile As String, mzmlFolder As String, qcode_exception As String)

        If flagCorruptedFile = False Then

            Dim additional_filter = ""
            Dim process As New Process()

            If qcode_exception = "QC1C" Or qcode_exception = "QC2C" Or qcode_exception = "cal" Then 'TTOF

                Log(getCurrentLogDate() & "[wiff to mzML] =============================================================================================================", w)
                If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[raw to mzML] Converting file " & rawFile & " to mzML.")
                Log(getCurrentLogDate() & "[wiff to mzML] Converting file " & rawFile & " to mzML.", w)
                Log(getCurrentLogDate() & "[wiff to mzML] Command: qtofpeakpicker.exe --in " & rawFile & " --out " & mzmlFolder & "\" & Path.GetFileNameWithoutExtension(rawFile) & ".mzML", w)
                If lbLog.Items.Count >= 35 Then cleanListBox() Else If cbDebugMode.IsChecked Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Command: qtofpeakpicker.exe --in " & rawFile & " --out " & mzmlFolder & "\" & Path.GetFileNameWithoutExtension(rawFile) & ".mzML")
                Console.Write("[DEBUG MODE] Command: qtofpeakpicker.exe --in " & rawFile & " --out " & mzmlFolder & "\" & Path.GetFileNameWithoutExtension(rawFile) & ".mzML")

                process.StartInfo.FileName = "qtofpeakpicker.exe"
                process.StartInfo.Arguments = "--in " & rawFile & " --out " & mzmlFolder & "\" & Path.GetFileNameWithoutExtension(rawFile) & ".mzML"

            Else 'ALL the rest

                If qcode_exception = "QC2Z" Then
                    additional_filter = "--filter ""threshold bpi-relative .01 most-intense"""
                ElseIf qcode_exception = "QC2E" Then
                    additional_filter = "--filter ""threshold bpi-relative .01 most-intense"""
                ElseIf qcode_exception = "QC2H" Then
                    additional_filter = "--filter ""threshold bpi-relative .01 most-intense"""
                End If

                Log(getCurrentLogDate() & "[raw to mzML] =============================================================================================================", w)
                If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[raw to mzML] Converting file " & rawFile & " to mzML.")
                Log(getCurrentLogDate() & "[raw to mzML] Converting file " & rawFile & " to mzML.", w)
                Log(getCurrentLogDate() & "[raw to mzML] Command: " & "msconvert.exe " & rawFile & " --32 --mzML --zlib --filter ""peakPicking true 1-"" " & additional_filter & " --outfile " & Path.GetFileNameWithoutExtension(rawFile) & " -o " & mzmlFolder, w)
                If lbLog.Items.Count >= 35 Then cleanListBox() Else If cbDebugMode.IsChecked Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Command: " & "msconvert.exe " & rawFile & " --32 --mzML --zlib --filter ""peakPicking true 1-"" " & additional_filter & " --outfile " & Path.GetFileNameWithoutExtension(rawFile) & " -o " & mzmlFolder)
                Console.Write("[DEBUG MODE] Command: " & "msconvert.exe " & rawFile & " --32 --mzML --zlib --filter ""peakPicking true 1-"" " & additional_filter & " --outfile " & Path.GetFileNameWithoutExtension(rawFile) & " -o " & mzmlFolder)

                process.StartInfo.FileName = "msconvert.exe"
                process.StartInfo.Arguments = rawFile & " --32 --mzML --zlib --filter ""peakPicking true 1-"" " & additional_filter & " --outfile " & Path.GetFileNameWithoutExtension(rawFile) & " -o " & mzmlFolder

            End If

            process.StartInfo.RedirectStandardOutput = True
            process.StartInfo.WindowStyle = ProcessWindowStyle.Hidden
            process.StartInfo.UseShellExecute = False
            Dim listFiles As System.Diagnostics.Process
            Console.Write(process.StartInfo.FileName)
            listFiles = System.Diagnostics.Process.Start(process.StartInfo)
            Dim myOutput As System.IO.StreamReader = listFiles.StandardOutput
            listFiles.WaitForExit()

            If listFiles.HasExited Then

                Dim output As String = myOutput.ReadToEnd

                If output.IndexOf("know how to read") <> -1 Or output.IndexOf("Unable to open file") <> -1 Or output.IndexOf("The handle is invalid") <> -1 Then
                    counterMsconvert = counterMsconvert + 1
                    Log(getCurrentLogDate() & "[WARNING] msconvert is not able to read, open or handle the file.", w)
                    If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[WARNING] msconvert is not able to read, open or handle the file.")
                    If counterMsconvert >= msconvertMaxTrials Then
                        Log(getCurrentLogDate() & "[WARNING] The file " & rawFile & "is CORRUPTED. Renaming and skipping it.", w)
                        If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[WARNING] The file is CORRUPTED. Renaming and skipping it.")
                        manageCorruptedFile(rawFile)
                        counterMsconvert = 0
                    End If

                End If

                Log(getCurrentLogDate() & "[raw to mzML] Output: " & output, w)

                Log(getCurrentLogDate() & "[raw to mzML] =============================================================================================================", w)

            End If

        End If

    End Sub

    Private Sub runMsXMLconvertProcess(rawFile As String, mzxmlFolder As String, qcode_exception As String)
        If flagCorruptedFile = False Then

            Dim additional_filter = ""
            If qcode_exception = "QC2Z" Then
                additional_filter = "--filter ""threshold bpi-relative .01 most-intense"""
            ElseIf qcode_exception = "QC2E" Then
                additional_filter = "--filter ""threshold bpi-relative .01 most-intense"""
            End If

            Log(getCurrentLogDate() & "[raw to mzXML] =============================================================================================================", w)
            If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[raw to mzXML] Converting file to mzXML.")
            Log(getCurrentLogDate() & "[raw to mzXML] Converting file " & rawFile & " to mXML.", w)
            Log(getCurrentLogDate() & "[raw to mzXML] Command: " & "msconvert.exe " & rawFile & " --32 --mzXML --zlib --filter peakPicking true 1 " & additional_filter & " --outfile " & Path.GetFileNameWithoutExtension(rawFile) & " -o " & mzxmlFolder, w)
            If lbLog.Items.Count >= 35 Then cleanListBox() Else If cbDebugMode.IsChecked Then lbLog.Items.Insert(0, getCurrentLogDate() & "[DEBUG MODE] Command: " & "msconvert.exe " & rawFile & " --32 --mzXML --zlib --filter peakPicking true 1 " & additional_filter & " --outfile " & Path.GetFileNameWithoutExtension(rawFile) & " -o " & mzxmlFolder)

            Dim process As New Process()
            process.StartInfo.FileName = "msconvert.exe"
            process.StartInfo.Arguments = rawFile & " --32 --mzXML --zlib --filter peakPicking true 1 " & additional_filter & " --outfile " & Path.GetFileNameWithoutExtension(rawFile) & " -o " & mzxmlFolder
            process.StartInfo.RedirectStandardOutput = True
            process.StartInfo.WindowStyle = ProcessWindowStyle.Hidden
            process.StartInfo.UseShellExecute = False
            Dim listFiles As System.Diagnostics.Process
            Console.Write(process.StartInfo.FileName)
            listFiles = System.Diagnostics.Process.Start(process.StartInfo)
            Dim myOutput As System.IO.StreamReader = listFiles.StandardOutput
            listFiles.WaitForExit()
            If listFiles.HasExited Then
                Dim output As String = myOutput.ReadToEnd
                Log(getCurrentLogDate() & "[raw to mzXML] Output: " & output, w)
                Log(getCurrentLogDate() & "[raw to mzXML] =============================================================================================================", w)
            End If

        End If
    End Sub

    Private Function checkFoldersAndGetMzml(root As String) As String

        Dim output As String = ""

        Try
            ' Check root directory: 
            Directory.SetCurrentDirectory(root)
            'Set mzML directory
            Directory.SetCurrentDirectory("../../../")
            output = Directory.GetCurrentDirectory
        Catch e As DirectoryNotFoundException
            If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "[ERROR] Raw folder does not exist :" & root)
            Log(getCurrentLogDate() & "[ERROR] Raw folder does not exist :" & root, w)
        End Try

        Return output

    End Function

    Private Function getQCodeFromFilename(filename As String) As String
        Dim output As String
        If filename.IndexOf("QC") <> -1 Then
            output = filename.Substring(filename.IndexOf("QC") + 0, 4)
        ElseIf filename.IndexOf("cal") <> -1 Then
            output = "cal"
        Else
            output = ""
        End If
        Return output
    End Function

    Private Function IsFileInUse(filename As String) As Boolean
        Dim Locked As Boolean = False
        Try
            If filename.IndexOf("\Wiff\") <> -1 Or filename.IndexOf("\wiff\") <> -1 Then
                Dim fsWiff As FileStream = File.Open(filename, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None)
                fsWiff.Close()
                Dim fsScan As FileStream = File.Open(filename & ".scan", FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None)
                fsScan.Close()
            Else
                Dim fs As FileStream = File.Open(filename, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None)
                fs.Close()
            End If
        Catch ex As IOException
            Locked = True
        Catch exgen As Exception
            Console.Write(getCurrentLogDate() & "[ERROR] Couldn't check if the file is in use or not (is probably corrupted).")
        End Try
        Return Locked
    End Function

    Private Function getMzmlFolder(qcode As String) As String
        Dim output As String = ""
        If qcode = "QC1L" Or qcode = "QC1Q" Or qcode = "QC2Q" Then
            output = "mzml"
        Else
            output = "mzML"
        End If
        Return output
    End Function

    Private Sub bStartSync_Click(sender As Object, e As RoutedEventArgs) Handles bStartSync.Click

        If cbDebugMode.IsChecked Then
            myTimer.Interval = debugRefreshInterval
        Else
            myTimer.Interval = productionRefreshInterval
        End If

        myTimer.Start()

        bStartSync.IsEnabled = False
        bStopSync.IsEnabled = True

        If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "START monitoring new RAW files...")
        Log(getCurrentLogDate() & "START monitoring new RAW files...", w)

    End Sub

    Private Sub bStopSync_Click(sender As Object, e As RoutedEventArgs) Handles bStopSync.Click
        If bw.WorkerSupportsCancellation = True Then
            bw.CancelAsync()
        End If
        bStartSync.IsEnabled = True
        bStopSync.IsEnabled = False
        myTimer.Stop()
        stopped = True
        If lbLog.Items.Count >= 35 Then cleanListBox() Else lbLog.Items.Insert(0, getCurrentLogDate() & "STOP monitoring new RAW files." & monitoredFolder)
        Log(getCurrentLogDate() & "STOP monitoring new RAW files at.", w)
    End Sub

    Private Sub bClearLog_Click(sender As Object, e As RoutedEventArgs) Handles bClearLog.Click
        lbLog.Items.Clear()
    End Sub

    Private Sub bExit_Click(ByVal sender As System.Object, ByVal e As System.EventArgs) Handles bExit.Click
        Close()
    End Sub

    Private Sub button_Click(sender As Object, e As RoutedEventArgs) Handles button.Click
        Me.WindowState = FormWindowState.Minimized
        If Me.WindowState = FormWindowState.Minimized Then
            notifyIcon.Visible = True
            notifyIcon.Icon = SystemIcons.Application
            notifyIcon.BalloonTipIcon = ToolTipIcon.Info
            notifyIcon.BalloonTipTitle = "QConverter minimized!"
            notifyIcon.BalloonTipText = "Please click again to maximize"
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

    Private Sub cleanListBox()
        If lbLog.Items.Count > 0 Then
            lbLog.SelectedIndex = lbLog.Items.Count - 1
            lbLog.Items.RemoveAt(lbLog.SelectedIndex)
        End If
    End Sub

    Private Sub checkFileSize(filename As String)

        Dim flagIsWiff As Boolean = False
        Dim flagHasZeroSize As Boolean = False

        If filename.IndexOf("\Wiff\") <> -1 Or filename.IndexOf("\wiff\") <> -1 Then
            flagIsWiff = True
        End If

        If flagIsWiff Then
            If My.Computer.FileSystem.GetFileInfo(filename).Length = 0 Then
                manageCorruptedFile(filename)
            End If
            If My.Computer.FileSystem.GetFileInfo(filename & ".scan").Length = 0 Then
                manageCorruptedFile(filename & ".scan")
            End If
        Else
            If My.Computer.FileSystem.GetFileInfo(filename).Length = 0 Then
                manageCorruptedFile(filename)
            End If
        End If

    End Sub

    Private Sub filesManager(myObject As Object, myEventArgs As EventArgs)
        Console.Write(getCurrentLogDate() & "Checking monitored local folder..." & vbCrLf)
        Dispatcher.Invoke(Sub()

                              myTimer.Stop()

                              rootRawFoldersList.Clear()

                              If cbDebugMode.IsChecked Then

                                  'rootRawFoldersList.Add("Z:\nodes\_test\OrbitestA\Raw\" & getCurrentMonthFolder() & "\QC01\")
                                  'rootRawFoldersList.Add("Z:\nodes\_test\QtrapA\wiff\" & getCurrentMonthFolder() & "\QC1L\")
                                  'rootRawFoldersList.Add("Z:\data\orbitrap_fusion\Raw\" & getCurrentMonthFolder() & "\QC1F\") 'Lumos
                                  'rootRawFoldersList.Add("Z:\nodes\eth\ttof1\Wiff\" & getCurrentMonthFolder() & "\QC1C\")
                                  'rootRawFoldersList.Add("Z:\nodes\eth\ttof1\Wiff\" & getCurrentMonthFolder() & "\QC2C\")
                                  rootRawFoldersList.Add("Z:\rolivella\myframeworks\qcweb\scripts\input\TripleTOF2\Wiff\" & getCurrentMonthFolder() & "\QCT1\") 'TTOF2
                                  rootRawFoldersList.Add("Z:\rolivella\myframeworks\qcweb\scripts\input\_orbitrap_xl\Raw\" & getCurrentMonthFolder() & "\QC1X\") 'XL

                              Else

                                  ' CRG:
                                  rootRawFoldersList.Add("Z:\data\orbitrap_velos\Raw\" & getCurrentMonthFolder() & "\QC1V\") 'Velos
                                  rootRawFoldersList.Add("Z:\data\orbitrap_velos\Raw\" & getCurrentMonthFolder() & "\QC2V\") ' Velos
                                  rootRawFoldersList.Add("Z:\data\orbitrap_fusion\Raw\" & getCurrentMonthFolder() & "\QC1F\") 'Lumos
                                  rootRawFoldersList.Add("Z:\data\orbitrap_fusion\Raw\" & getCurrentMonthFolder() & "\QC2F\") 'Lumos
                                  rootRawFoldersList.Add("Z:\data\orbitrap_fusion\Raw\" & getCurrentMonthFolder() & "\QC2H\") 'Lumos
                                  rootRawFoldersList.Add("Z:\data\orbitrap_xl\Raw\" & getCurrentMonthFolder() & "\QC1X\") 'XL
                                  rootRawFoldersList.Add("Z:\data\orbitrap_xl\Raw\" & getCurrentMonthFolder() & "\QC2X\") 'XL
                                  rootRawFoldersList.Add("Z:\data\QQQ\wiff\" & getCurrentMonthFolder() & "\QC1L\") 'Qtrap
                                  rootRawFoldersList.Add("Z:\data\QQQ\wiff\" & getCurrentMonthFolder() & "\QC2Q\") 'Qtrap
                                  rootRawFoldersList.Add("Z:\data\TripleTOF2\Wiff\" & getCurrentMonthFolder() & "\QCT1\") 'TTOF2

                                  rootRawFoldersList.Add("Z:\data\orbitrap_xl\Raw\" & getCurrentMonthFolder() & "\QCFX\") 'FASP
                                  rootRawFoldersList.Add("Z:\data\orbitrap_xl\Raw\" & getCurrentMonthFolder() & "\QCGX\") 'InGel
                                  rootRawFoldersList.Add("Z:\data\orbitrap_xl\Raw\" & getCurrentMonthFolder() & "\QCDX\") 'InSolution
                                  rootRawFoldersList.Add("Z:\data\orbitrap_xl\Raw\" & getCurrentMonthFolder() & "\QCRP\") 'Agilent
                                  rootRawFoldersList.Add("Z:\data\orbitrap_velos\Raw\" & getCurrentMonthFolder() & "\QCPV\") 'Phospho

                                  ' Zurich:
                                  rootRawFoldersList.Add("Z:\nodes\fgcz\QEXACTIVEHF_1\Raw\" & getCurrentMonthFolder() & "\QCI1\")
                                  rootRawFoldersList.Add("Z:\nodes\fgcz\QEXACTIVEHF_1\Raw\" & getCurrentMonthFolder() & "\QCI2\")
                                  rootRawFoldersList.Add("Z:\nodes\fgcz\QEXACTIVEHF_2\Raw\" & getCurrentMonthFolder() & "\QCJ1\")
                                  rootRawFoldersList.Add("Z:\nodes\fgcz\QEXACTIVEHF_2\Raw\" & getCurrentMonthFolder() & "\QCJ2\")
                                  rootRawFoldersList.Add("Z:\nodes\fgcz\QEXACTIVE_2\Raw\" & getCurrentMonthFolder() & "\QC1Z\")
                                  rootRawFoldersList.Add("Z:\nodes\fgcz\QEXACTIVE_2\Raw\" & getCurrentMonthFolder() & "\QC2Z\")
                                  rootRawFoldersList.Add("Z:\nodes\fgcz\FUSION_1\Raw\" & getCurrentMonthFolder() & "\QC1R\")
                                  rootRawFoldersList.Add("Z:\nodes\fgcz\FUSION_1\Raw\" & getCurrentMonthFolder() & "\QC2R\")
                                  rootRawFoldersList.Add("Z:\nodes\fgcz\FUSION_2\Raw\" & getCurrentMonthFolder() & "\QCK1\")
                                  rootRawFoldersList.Add("Z:\nodes\fgcz\FUSION_2\Raw\" & getCurrentMonthFolder() & "\QCK2\")

                                  ' Ghent: 
                                  rootRawFoldersList.Add("Z:\nodes\vib\emanuella_1p\Raw\" & getCurrentMonthFolder() & "\QC1G\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\emanuella_1p\Raw\" & getCurrentMonthFolder() & "\QC2G\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\emanuella_2p\Raw\" & getCurrentMonthFolder() & "\QC1K\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\emanuella_2p\Raw\" & getCurrentMonthFolder() & "\QC2K\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\heidi\Raw\" & getCurrentMonthFolder() & "\QC1M\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\heidi\Raw\" & getCurrentMonthFolder() & "\QC2M\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\berta_ap\Raw\" & getCurrentMonthFolder() & "\QC1B\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\berta_ap\Raw\" & getCurrentMonthFolder() & "\QC2B\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\berta_bp\Raw\" & getCurrentMonthFolder() & "\QC1J\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\berta_bp\Raw\" & getCurrentMonthFolder() & "\QC2J\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\erika_1p\Raw\" & getCurrentMonthFolder() & "\QC1P\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\erika_1p\Raw\" & getCurrentMonthFolder() & "\QC2P\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\erika_2p\Raw\" & getCurrentMonthFolder() & "\QC1W\")
                                  rootRawFoldersList.Add("Z:\nodes\vib\erika_2p\Raw\" & getCurrentMonthFolder() & "\QC2W\")

                                  ' ETH:
                                  rootRawFoldersList.Add("Z:\nodes\eth\ttof1\Wiff\" & getCurrentMonthFolder() & "\QC1C\")
                                  rootRawFoldersList.Add("Z:\nodes\eth\ttof1\Wiff\" & getCurrentMonthFolder() & "\QC2C\")

                                  ' EMBL: 
                                  rootRawFoldersList.Add("Z:\nodes\embl\orbitrap\Raw\" & getCurrentMonthFolder() & "\QC1E\")
                                  rootRawFoldersList.Add("Z:\nodes\embl\orbitrap\Raw\" & getCurrentMonthFolder() & "\QC2E\")
                                  rootRawFoldersList.Add("Z:\nodes\embl\ernie\Raw\" & getCurrentMonthFolder() & "\QCE1\")
                                  rootRawFoldersList.Add("Z:\nodes\embl\ernie\Raw\" & getCurrentMonthFolder() & "\QCE2\")
                                  rootRawFoldersList.Add("Z:\nodes\embl\bert\Raw\" & getCurrentMonthFolder() & "\QCB1\")
                                  rootRawFoldersList.Add("Z:\nodes\embl\bert\Raw\" & getCurrentMonthFolder() & "\QCB2\")

                                  ' Dresden: 
                                  rootRawFoldersList.Add("Z:\nodes\mpi\orbitrap_velos\Raw\" & getCurrentMonthFolder() & "\QC1D\")
                                  rootRawFoldersList.Add("Z:\nodes\mpi\orbitrap_velos\Raw\" & getCurrentMonthFolder() & "\QC2D\")
                                  rootRawFoldersList.Add("Z:\nodes\mpi\qexactive_hf\Raw\" & getCurrentMonthFolder() & "\QC1O\")
                                  rootRawFoldersList.Add("Z:\nodes\mpi\qexactive_hf\Raw\" & getCurrentMonthFolder() & "\QC2O\")

                                  ' CPR: 
                                  rootRawFoldersList.Add("Z:\nodes\cpr\qexactive_hf_1\Raw\" & getCurrentMonthFolder() & "\QC1I\")
                                  rootRawFoldersList.Add("Z:\nodes\cpr\qexactive_hf_1\Raw\" & getCurrentMonthFolder() & "\QC2I\")

                                  ' Ferrer's lab: 
                                  rootRawFoldersList.Add("Z:\nodes\ferrer\qh_1\Raw\" & getCurrentMonthFolder() & "\QCF1\")
                                  rootRawFoldersList.Add("Z:\nodes\ferrer\qh_1\Raw\" & getCurrentMonthFolder() & "\QCF2\")

                                  ' PEL (caltech) lab: 
                                  rootRawFoldersList.Add("Z:\nodes\pel\qehf\Raw\" & getCurrentMonthFolder() & "\QCA1\")
                                  rootRawFoldersList.Add("Z:\nodes\pel\qehf\Raw\" & getCurrentMonthFolder() & "\QCA2\")

                                  ' MIPBN (Joh Graumann) lab: 
                                  rootRawFoldersList.Add("Z:\nodes\mpibn\qehf\Raw\" & getCurrentMonthFolder() & "\QCC1\")
                                  rootRawFoldersList.Add("Z:\nodes\mpibn\qehf\Raw\" & getCurrentMonthFolder() & "\QCC2\")

                                  ' CNIO (Ana del Val) lab: 
                                  rootRawFoldersList.Add("Z:\nodes\cnio\orbitrap_velos\Raw\" & getCurrentMonthFolder() & "\QCH1\")
                                  rootRawFoldersList.Add("Z:\nodes\cnio\orbitrap_velos\Raw\" & getCurrentMonthFolder() & "\QCH2\")

                              End If

                              counterProcessFiles = 0

                              For Each rootfolder In rootRawFoldersList
                                  Dim comparelist As List(Of String) = compareSourceAndMzmlFolders(rootfolder, checkFoldersAndGetMzml(rootfolder) & "\" & getMzmlFolder(getQCodeFromFilename(rootfolder)) & "\" & getCurrentMonthFolder() & "\")
                                  If comparelist.Count Then
                                      For Each file In comparelist
                                          stopped = False
                                          If IsFileInUse(rootfolder & file) = False Then
                                              If file.IndexOf("CORRUPTED") = -1 Then
                                                  runMzMLconvertProcess(rootfolder & file, checkFoldersAndGetMzml(rootfolder) & "\" & getMzmlFolder(getQCodeFromFilename(rootfolder)) & "\" & getCurrentMonthFolder(), getQCodeFromFilename(rootfolder))
                                              End If
                                          End If
                                          flagCorruptedFile = False
                                          counterProcessFiles = counterProcessFiles + 1
                                          If counterProcessFiles = maxProcessFiles Then
                                              Exit For
                                          End If
                                      Next
                                  End If
                              Next
                              myTimer.Start() 'Restart

                          End Sub)
    End Sub

End Class