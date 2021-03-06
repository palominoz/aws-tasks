/**
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package datameer.awstasks.ssh;

import static org.hamcrest.Matchers.*;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

import datameer.awstasks.AbstractTest;
import datameer.awstasks.exec.ShellCommand;
import datameer.awstasks.exec.command.FreeFormCommand;
import datameer.awstasks.exec.handler.ExecCaptureLineHandler;
import datameer.awstasks.exec.handler.ExecCaptureLinesHandler;
import datameer.awstasks.exec.handler.ExecExitCodeHandler;
import datameer.awstasks.testsupport.junit.CheckBefore;
import datameer.awstasks.testsupport.junit.CheckBeforeRunner;
import datameer.awstasks.util.IoUtil;

/**
 * 
 * In order to run this test successfully passwordless access to localhost needs to ba enabled on
 * your system.
 * 
 */
@RunWith(CheckBeforeRunner.class)
public class JschRunnerTest extends AbstractTest {

    private static final String USER = System.getProperty("user.name");
    private static final String HOST = "localhost";

    @CheckBefore
    public void checkBefore() throws Exception {
        JschRunner jschRunner = createJschRunner();
        jschRunner.testConnect();
        // should be succesfull if you can connect passphraseless to localhost
    }

    private JschRunner createJschRunner() {
        JschRunner jschRunner = new JschRunner(USER, HOST);
        jschRunner.setKeyfile(JschRunner.findStandardKeyFile(false));
        return jschRunner;
    }

    @Test
    public void testConnectWithKeyFileContent() throws Exception {
        File keyFile = JschRunner.findStandardKeyFile(true);
        String keyFileContent = readKeyFile(keyFile);

        JschRunner jschRunner = new JschRunner(USER, HOST);
        jschRunner.setKeyfileContent(keyFileContent);

        ShellCommand<?> command = new ShellCommand<List<String>>(new String[] { "ls", "/" }, true);
        jschRunner.execute(command);
    }

    private String readKeyFile(File keyFile) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(keyFile)));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append("\n");
        }
        return builder.toString();

    }

    @Test
    public void testUpload() throws Exception {
        JschRunner jschRunner = createJschRunner();
        File sourceFile = new File("build.xml");
        File destFile = new File(_tempFolder.newFolder("folder"), "file");
        assertFalse(destFile.exists());

        jschRunner.run(new ScpUploadCommand(sourceFile, destFile.getAbsolutePath()));
        assertTrue(destFile.exists());
        assertEquals(sourceFile.length(), destFile.length());
    }

    @Test
    public void testUploadDownloadWithWhitespaceInName() throws Exception {
        JschRunner jschRunner = createJschRunner();
        File uploadSrcFile = _tempFolder.newFile("a file");
        File uploadDestFolder = _tempFolder.newFolder("upload");
        File uploadDestFile = new File(uploadDestFolder, uploadSrcFile.getName());
        File downloadDestFolder = _tempFolder.newFolder("download");
        File downloadDestFile = new File(downloadDestFolder, uploadSrcFile.getName());
        assertFalse(uploadDestFile.exists());

        jschRunner.run(new ScpUploadCommand(uploadSrcFile, uploadDestFolder.getAbsolutePath()));
        assertTrue(uploadDestFile.exists());
        assertEquals(uploadSrcFile.length(), downloadDestFile.length());

        jschRunner.run(new ScpDownloadCommand(uploadDestFile.getAbsolutePath(), downloadDestFolder, false));
        assertTrue(downloadDestFile.exists());
        assertEquals(uploadSrcFile.length(), downloadDestFile.length());
    }

    @Test
    public void testOpen() throws Exception {
        JschRunner jschRunner = createJschRunner();
        InputStream inputStream = jschRunner.openFile(new File("build.xml").getAbsolutePath());
        int available = inputStream.available();
        ByteArrayOutputStream byteOutStream = new ByteArrayOutputStream();

        assertThat(available, greaterThan(0));
        IoUtil.copyBytes(inputStream, byteOutStream);
        assertEquals(available, byteOutStream.size());
        inputStream.close();
    }

    @Test
    public void testCreate() throws Exception {
        File file = _tempFolder.newFile("remoteFile");
        file.delete();
        String message = "hello world";

        JschRunner jschRunner = createJschRunner();
        OutputStream outputStream = jschRunner.createFile(file.getAbsolutePath(), message.getBytes().length);
        outputStream.write(message.getBytes());
        outputStream.close();

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line = reader.readLine();
        assertEquals(message, line);
        assertNull(reader.readLine());
        reader.close();
    }

    @Test
    public void testOpen_WhitespaceInName() throws Exception {
        File aFile = _tempFolder.newFile("a file");
        JschRunner jschRunner = createJschRunner();
        InputStream inputStream = jschRunner.openFile(aFile.getAbsolutePath());
        inputStream.close();
    }

    @Test
    public void testExecuteShellCommand() throws Exception {
        JschRunner jschRunner = createJschRunner();
        ShellCommand<?> command = new ShellCommand<List<String>>(new String[] { "ls", "/" }, true);
        List<String> result = jschRunner.execute(command, new ExecCaptureLinesHandler());
        assertFalse(result.isEmpty());
    }

    @Test
    public void testExecuteShellCommandWithNoEofOutput() throws Exception {
        JschRunner jschRunner = createJschRunner();
        ShellCommand<List<String>> command = new ShellCommand<List<String>>(new String[] { "ls", "-dl", "/" }, true);
        String result = jschRunner.execute(command, new ExecCaptureLineHandler());
        assertNotNull(result);
    }

    @Test
    public void testExecuteShellCommandCompleteOutput() throws Exception {
        JschRunner jschRunner = createJschRunner();
        ShellCommand<List<String>> command = new ShellCommand<List<String>>(new String[] { "ls", "-Al", new File(".").getAbsolutePath() }, true);
        List<String> result = jschRunner.execute(command, new ExecCaptureLinesHandler());
        assertThat(result.get(0), startsWith("total"));
        for (int i = 1; i < result.size(); i++) {
            assertEquals(9, split(result.get(i), " ").length);
        }
        assertNotNull(result);
    }

    @Test
    public void testExecuteShellCommand_LineDoesNotEndWithNewLine() throws Exception {
        _tempFolder.newFile("file1");
        _tempFolder.newFile("file2");
        JschRunner jschRunner = createJschRunner();
        FreeFormCommand existsCommand = new FreeFormCommand("ls", "-lA", _tempFolder.getRoot().getAbsolutePath());
        existsCommand.setFailOnError(false);
        ExecCaptureLinesHandler lineHandler = new ExecCaptureLinesHandler();
        int exitCode = jschRunner.execute(existsCommand, new ExecExitCodeHandler(), lineHandler);
        assertEquals(0, exitCode);
        List<String> lines = lineHandler.getResult(exitCode);
        lines.remove(0);// total line
        assertEquals(2, lines.size());
        for (String line : lines) {
            System.out.println("'" + line + "'");
            assertFalse(line.endsWith("\n"));
        }
    }

    private static String[] split(String string, String regex) {
        List<String> splitList = new ArrayList<String>();
        String[] splits = string.split(regex);
        for (String split : splits) {
            if (split.length() > 0) {
                splitList.add(split);
            }
        }
        return splitList.toArray(new String[splitList.size()]);
    }
}
