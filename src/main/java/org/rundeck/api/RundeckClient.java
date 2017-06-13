/*
 * Copyright 2011 Vincent Behar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rundeck.api;

import static java.lang.String.format;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.rundeck.api.RundeckApiException.RundeckApiLoginException;
import org.rundeck.api.RundeckApiException.RundeckApiTokenException;
import org.rundeck.api.domain.RundeckAbort;
import org.rundeck.api.domain.RundeckExecution;
import org.rundeck.api.domain.RundeckExecution.ExecutionStatus;
import org.rundeck.api.domain.RundeckHistory;
import org.rundeck.api.domain.RundeckJob;
import org.rundeck.api.domain.RundeckJobsImportMethod;
import org.rundeck.api.domain.RundeckJobsImportResult;
import org.rundeck.api.domain.RundeckNode;
import org.rundeck.api.domain.RundeckProject;
import org.rundeck.api.domain.RundeckSystemInfo;
import org.rundeck.api.parser.AbortParser;
import org.rundeck.api.parser.ExecutionParser;
import org.rundeck.api.parser.HistoryParser;
import org.rundeck.api.parser.JobParser;
import org.rundeck.api.parser.JobsImportResultParser;
import org.rundeck.api.parser.ListParser;
import org.rundeck.api.parser.NodeParser;
import org.rundeck.api.parser.ProjectParser;
import org.rundeck.api.parser.StringParser;
import org.rundeck.api.parser.SystemInfoParser;
import org.rundeck.api.util.AssertUtil;
import org.rundeck.api.util.ParametersUtil;

/**
 * Main entry point to talk to a RunDeck instance.<br>
 * You have 2 methods for authentication : login-based or token-based. If you want to use the first, you need to provide
 * both a "login" and a "password". Otherwise, just provide a "token" (also called "auth-token"). See the RunDeck
 * documentation for generating such a token.<br>
 * <br>
 * Usage : <br>
 * <code>
 * <pre class="prettyprint">
 * // using login-based authentication :
 * RundeckClient rundeck = new RundeckClient("http://localhost:4440", "admin", "admin");
 * // or for a token-based authentication :
 * RundeckClient rundeck = new RundeckClient("http://localhost:4440", "PDDNKo5VE29kpk4prOUDr2rsKdRkEvsD");
 * 
 * List&lt;RundeckProject&gt; projects = rundeck.getProjects();
 * 
 * RundeckJob job = rundeck.findJob("my-project", "main-group/sub-group", "job-name");
 * RundeckExecution execution = rundeck.triggerJob(job.getId(),
 *                                                 new OptionsBuilder().addOption("version", "1.2.0").toProperties());
 * 
 * List&lt;RundeckExecution&gt; runningExecutions = rundeck.getRunningExecutions("my-project");
 * 
 * rundeck.exportJobsToFile("/tmp/jobs.xml", FileType.XML, "my-project");
 * rundeck.importJobs("/tmp/jobs.xml", FileType.XML);
 * </pre>
 * </code>
 * 
 * @author Vincent Behar
 */
public class RundeckClient implements Serializable {

    private static final long serialVersionUID = 1L;


    /** Default value for the "pooling interval" used when running jobs/commands/scripts */
    private static final transient long DEFAULT_POOLING_INTERVAL = 5;

    /** Default unit of the "pooling interval" used when running jobs/commands/scripts */
    private static final transient TimeUnit DEFAULT_POOLING_UNIT = TimeUnit.SECONDS;

    /** URL of the RunDeck instance ("http://localhost:4440", "http://rundeck.your-compagny.com/", etc) */
    private final String url;

    /** Auth-token for authentication (if not using login-based auth) */
    private final String token;

    /** Login to use for authentication on the RunDeck instance (if not using token-based auth) */
    private final String login;

    /** Password to use for authentication on the RunDeck instance (if not using token-based auth) */
    private final String password;
    public static final Gson GSON = new Gson();

    /**
     * Instantiate a new {@link RundeckClient} for the RunDeck instance at the given url, using login-based
     * authentication.
     * 
     * @param url of the RunDeck instance ("http://localhost:4440", "http://rundeck.your-compagny.com/", etc)
     * @param login to use for authentication on the RunDeck instance
     * @param password to use for authentication on the RunDeck instance
     * @throws IllegalArgumentException if the url, login or password is blank (null, empty or whitespace)
     */
    public RundeckClient(String url, String login, String password) throws IllegalArgumentException {
        super();
        AssertUtil.notBlank(url, "The RunDeck URL is mandatory !");
        AssertUtil.notBlank(login, "The RunDeck login is mandatory !");
        AssertUtil.notBlank(password, "The RunDeck password is mandatory !");
        this.url = url;
        this.login = login;
        this.password = password;
        this.token = null;
    }

    /**
     * Instantiate a new {@link RundeckClient} for the RunDeck instance at the given url, using token-based
     * authentication.
     * 
     * @param url of the RunDeck instance ("http://localhost:4440", "http://rundeck.your-compagny.com/", etc)
     * @param token to use for authentication on the RunDeck instance
     * @throws IllegalArgumentException if the url or token is blank (null, empty or whitespace)
     */
    public RundeckClient(String url, String token) throws IllegalArgumentException {
        super();
        AssertUtil.notBlank(url, "The RunDeck URL is mandatory !");
        AssertUtil.notBlank(token, "The RunDeck auth-token is mandatory !");
        this.url = url;
        this.token = token;
        this.login = null;
        this.password = null;
    }

    /**
     * Try to "ping" the RunDeck instance to see if it is alive
     * 
     * @throws RundeckApiException if the ping fails
     */
    public void ping() throws RundeckApiException {
        new ApiCall(this, RundeckAPI.TWO).ping();
    }

    /**
     * Test the authentication on the RunDeck instance.
     * 
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     */
    public void testAuth() throws RundeckApiLoginException, RundeckApiTokenException {
        new ApiCall(this, RundeckAPI.TWO).testAuth();
    }

    /**
     * @deprecated Use {@link #testAuth()}
     * @see #testAuth()
     */
    @Deprecated
    public void testCredentials() throws RundeckApiLoginException, RundeckApiTokenException {
        testAuth();
    }

    /*
     * Projects
     */

    /**
     * List all projects
     * 
     * @return a {@link List} of {@link RundeckProject} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     */
    public List<RundeckProject> getProjects() throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException {
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/projects"),
                                                     new ListParser<RundeckProject>(new ProjectParser(), "result/projects/project"));
    }

    /**
     * Get the definition of a single project, identified by the given name
     * 
     * @param projectName name of the project - mandatory
     * @return a {@link RundeckProject} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the projectName is blank (null, empty or whitespace)
     */
    public RundeckProject getProject(String projectName) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(projectName, "projectName is mandatory to get the details of a project !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/project/", projectName),
                                                     new ProjectParser("result/projects/project"));
    }

    /*
     * Jobs
     */

    /**
     * List all jobs (for all projects)
     * 
     * @return a {@link List} of {@link RundeckJob} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     */
    public List<RundeckJob> getJobs() throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException {
        List<RundeckJob> jobs = new ArrayList<RundeckJob>();
        for (RundeckProject project : getProjects()) {
            jobs.addAll(getJobs(project.getName()));
        }
        return jobs;
    }

    /**
     * List all jobs that belongs to the given project
     * 
     * @param project name of the project - mandatory
     * @return a {@link List} of {@link RundeckJob} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getJobs(String, String, String, String...)
     */
    public List<RundeckJob> getJobs(String project) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        return getJobs(project, null, null, new String[0]);
    }

    /**
     * List the jobs that belongs to the given project, and matches the given criteria (jobFilter, groupPath and jobIds)
     * 
     * @param project name of the project - mandatory
     * @param jobFilter a filter for the job Name - optional
     * @param groupPath a group or partial group path to include all jobs within that group path - optional
     * @param jobIds a list of Job IDs to include - optional
     * @return a {@link List} of {@link RundeckJob} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getJobs(String)
     */
    public List<RundeckJob> getJobs(String project, String jobFilter, String groupPath, String... jobIds)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(project, "project is mandatory to get all jobs !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/jobs").param("project", project)
                                                                .param("jobFilter", jobFilter)
                                                                .param("groupPath", groupPath)
                                                                .param("idlist", StringUtils.join(jobIds, ",")),
                                                     new ListParser<RundeckJob>(new JobParser(), "result/jobs/job"));
    }

    /**
     * Export the definitions of all jobs that belongs to the given project
     * 
     * @param filename path of the file where the content should be saved - mandatory
     * @param format of the export. See {@link FileType} - mandatory
     * @param project name of the project - mandatory
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the format or project is blank (null, empty or whitespace), or the format is
     *             invalid
     * @throws IOException if we failed to write to the file
     * @see #exportJobsToFile(String, FileType, String, String, String, String...)
     * @see #exportJobs(String, String)
     */
    public void exportJobsToFile(String filename, String format, String project) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        AssertUtil.notBlank(format, "format is mandatory to export jobs !");
        exportJobsToFile(filename, FileType.valueOf(StringUtils.upperCase(format)), project);
    }

    /**
     * Export the definitions of all jobs that belongs to the given project
     * 
     * @param filename path of the file where the content should be saved - mandatory
     * @param format of the export. See {@link FileType} - mandatory
     * @param project name of the project - mandatory
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the format is null
     * @throws IOException if we failed to write to the file
     * @see #exportJobsToFile(String, FileType, String, String, String, String...)
     * @see #exportJobs(FileType, String)
     */
    public void exportJobsToFile(String filename, FileType format, String project) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        exportJobsToFile(filename, format, project, null, null, new String[0]);
    }

    /**
     * Export the definitions of the jobs that belongs to the given project, and matches the given criteria (jobFilter,
     * groupPath and jobIds)
     * 
     * @param filename path of the file where the content should be saved - mandatory
     * @param format of the export. See {@link FileType} - mandatory
     * @param project name of the project - mandatory
     * @param jobFilter a filter for the job Name - optional
     * @param groupPath a group or partial group path to include all jobs within that group path - optional
     * @param jobIds a list of Job IDs to include - optional
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the filename, format or project is blank (null, empty or whitespace), or the
     *             format is invalid
     * @throws IOException if we failed to write to the file
     * @see #exportJobsToFile(String, FileType, String, String, String, String...)
     * @see #exportJobs(FileType, String, String, String, String...)
     */
    public void exportJobsToFile(String filename, String format, String project, String jobFilter, String groupPath,
            String... jobIds) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException, IOException {
        AssertUtil.notBlank(format, "format is mandatory to export jobs !");
        exportJobsToFile(filename,
                         FileType.valueOf(StringUtils.upperCase(format)),
                         project,
                         jobFilter,
                         groupPath,
                         jobIds);
    }

    /**
     * Export the definitions of the jobs that belongs to the given project, and matches the given criteria (jobFilter,
     * groupPath and jobIds)
     * 
     * @param filename path of the file where the content should be saved - mandatory
     * @param format of the export. See {@link FileType} - mandatory
     * @param project name of the project - mandatory
     * @param jobFilter a filter for the job Name - optional
     * @param groupPath a group or partial group path to include all jobs within that group path - optional
     * @param jobIds a list of Job IDs to include - optional
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the filename or project is blank (null, empty or whitespace), or the format
     *             is null
     * @throws IOException if we failed to write to the file
     * @see #exportJobs(FileType, String, String, String, String...)
     */
    public void exportJobsToFile(String filename, FileType format, String project, String jobFilter, String groupPath,
            String... jobIds) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException, IOException {
        AssertUtil.notBlank(filename, "filename is mandatory to export a job !");
        InputStream inputStream = exportJobs(format, project, jobFilter, groupPath, jobIds);
        FileUtils.writeByteArrayToFile(new File(filename), IOUtils.toByteArray(inputStream));
    }

    /**
     * Export the definitions of all jobs that belongs to the given project
     * 
     * @param format of the export. See {@link FileType} - mandatory
     * @param project name of the project - mandatory
     * @return an {@link InputStream} instance, not linked to any network resources - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the format or project is blank (null, empty or whitespace), or the format is
     *             invalid
     * @see #exportJobs(FileType, String, String, String, String...)
     * @see #exportJobsToFile(String, String, String)
     */
    public InputStream exportJobs(String format, String project) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(format, "format is mandatory to export jobs !");
        return exportJobs(FileType.valueOf(StringUtils.upperCase(format)), project);
    }

    /**
     * Export the definitions of all jobs that belongs to the given project
     * 
     * @param format of the export. See {@link FileType} - mandatory
     * @param project name of the project - mandatory
     * @return an {@link InputStream} instance, not linked to any network resources - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the format is null
     * @see #exportJobs(FileType, String, String, String, String...)
     * @see #exportJobsToFile(String, FileType, String)
     */
    public InputStream exportJobs(FileType format, String project) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return exportJobs(format, project, null, null, new String[0]);
    }

    /**
     * Export the definitions of the jobs that belongs to the given project, and matches the given criteria (jobFilter,
     * groupPath and jobIds)
     * 
     * @param format of the export. See {@link FileType} - mandatory
     * @param project name of the project - mandatory
     * @param jobFilter a filter for the job Name - optional
     * @param groupPath a group or partial group path to include all jobs within that group path - optional
     * @param jobIds a list of Job IDs to include - optional
     * @return an {@link InputStream} instance, not linked to any network resources - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the format or project is blank (null, empty or whitespace), or the format is
     *             invalid
     * @see #exportJobs(FileType, String, String, String, String...)
     * @see #exportJobsToFile(String, String, String, String, String, String...)
     */
    public InputStream exportJobs(String format, String project, String jobFilter, String groupPath, String... jobIds)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(format, "format is mandatory to export jobs !");
        return exportJobs(FileType.valueOf(StringUtils.upperCase(format)), project, jobFilter, groupPath, jobIds);
    }

    /**
     * Export the definitions of the jobs that belongs to the given project, and matches the given criteria (jobFilter,
     * groupPath and jobIds)
     * 
     * @param format of the export. See {@link FileType} - mandatory
     * @param project name of the project - mandatory
     * @param jobFilter a filter for the job Name - optional
     * @param groupPath a group or partial group path to include all jobs within that group path - optional
     * @param jobIds a list of Job IDs to include - optional
     * @return an {@link InputStream} instance, not linked to any network resources - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the format is null
     * @see #exportJobsToFile(String, FileType, String, String, String, String...)
     */
    public InputStream exportJobs(FileType format, String project, String jobFilter, String groupPath, String... jobIds)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notNull(format, "format is mandatory to export jobs !");
        AssertUtil.notBlank(project, "project is mandatory to export jobs !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/jobs/export").param("format", format)
                                                                       .param("project", project)
                                                                       .param("jobFilter", jobFilter)
                                                                       .param("groupPath", groupPath)
                                                                       .param("idlist", StringUtils.join(jobIds, ",")));
    }

    /**
     * Export the definition of a single job (identified by the given ID)
     * 
     * @param filename path of the file where the content should be saved - mandatory
     * @param format of the export. See {@link FileType} - mandatory
     * @param jobId identifier of the job - mandatory
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the filename, format or jobId is blank (null, empty or whitespace), or the
     *             format is invalid
     * @throws IOException if we failed to write to the file
     * @see #exportJobToFile(String, FileType, String)
     * @see #exportJob(String, String)
     * @see #getJob(String)
     */
    public void exportJobToFile(String filename, String format, String jobId) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        AssertUtil.notBlank(format, "format is mandatory to export a job !");
        exportJobToFile(filename, FileType.valueOf(StringUtils.upperCase(format)), jobId);
    }

    /**
     * Export the definition of a single job (identified by the given ID)
     * 
     * @param filename path of the file where the content should be saved - mandatory
     * @param format of the export. See {@link FileType} - mandatory
     * @param jobId identifier of the job - mandatory
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the filename or jobId is blank (null, empty or whitespace), or the format is
     *             null
     * @throws IOException if we failed to write to the file
     * @see #exportJob(FileType, String)
     * @see #getJob(String)
     */
    public void exportJobToFile(String filename, FileType format, String jobId) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        AssertUtil.notBlank(filename, "filename is mandatory to export a job !");
        InputStream inputStream = exportJob(format, jobId);
        FileUtils.writeByteArrayToFile(new File(filename), IOUtils.toByteArray(inputStream));
    }

    /**
     * Export the definition of a single job, identified by the given ID
     * 
     * @param format of the export. See {@link FileType} - mandatory
     * @param jobId identifier of the job - mandatory
     * @return an {@link InputStream} instance, not linked to any network resources - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the format or jobId is blank (null, empty or whitespace), or the format is
     *             invalid
     * @see #exportJobToFile(String, String, String)
     * @see #getJob(String)
     */
    public InputStream exportJob(String format, String jobId) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(format, "format is mandatory to export a job !");
        return exportJob(FileType.valueOf(StringUtils.upperCase(format)), jobId);
    }

    /**
     * Export the definition of a single job, identified by the given ID
     * 
     * @param format of the export. See {@link FileType} - mandatory
     * @param jobId identifier of the job - mandatory
     * @return an {@link InputStream} instance, not linked to any network resources - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace), or the format is null
     * @see #exportJobToFile(String, FileType, String)
     * @see #getJob(String)
     */
    public InputStream exportJob(FileType format, String jobId) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notNull(format, "format is mandatory to export a job !");
        AssertUtil.notBlank(jobId, "jobId is mandatory to export a job !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/job/", jobId).param("format", format));
    }

    /**
     * Import the definitions of jobs, from the given file
     * 
     * @param filename of the file containing the jobs definitions - mandatory
     * @param fileType type of the file. See {@link FileType} - mandatory
     * @return a {@link RundeckJobsImportResult} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the filename or fileType is blank (null, empty or whitespace), or the
     *             fileType is invalid
     * @throws IOException if we failed to read the file
     * @see #importJobs(InputStream, String)
     * @see #importJobs(String, FileType, RundeckJobsImportMethod)
     */
    public RundeckJobsImportResult importJobs(String filename, String fileType) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        AssertUtil.notBlank(fileType, "fileType is mandatory to import jobs !");
        return importJobs(filename, FileType.valueOf(StringUtils.upperCase(fileType)));
    }

    /**
     * Import the definitions of jobs, from the given file
     * 
     * @param filename of the file containing the jobs definitions - mandatory
     * @param fileType type of the file. See {@link FileType} - mandatory
     * @return a {@link RundeckJobsImportResult} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the filename is blank (null, empty or whitespace), or the fileType is null
     * @throws IOException if we failed to read the file
     * @see #importJobs(InputStream, FileType)
     * @see #importJobs(String, FileType, RundeckJobsImportMethod)
     */
    public RundeckJobsImportResult importJobs(String filename, FileType fileType) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        return importJobs(filename, fileType, (RundeckJobsImportMethod) null);
    }

    /**
     * Import the definitions of jobs, from the given file, using the given behavior
     * 
     * @param filename of the file containing the jobs definitions - mandatory
     * @param fileType type of the file. See {@link FileType} - mandatory
     * @param importBehavior see {@link RundeckJobsImportMethod}
     * @return a {@link RundeckJobsImportResult} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the filename or fileType is blank (null, empty or whitespace), or the
     *             fileType or behavior is not valid
     * @throws IOException if we failed to read the file
     * @see #importJobs(InputStream, String, String)
     * @see #importJobs(String, FileType, RundeckJobsImportMethod)
     */
    public RundeckJobsImportResult importJobs(String filename, String fileType, String importBehavior)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException,
            IOException {
        AssertUtil.notBlank(fileType, "fileType is mandatory to import jobs !");
        return importJobs(filename,
                          FileType.valueOf(StringUtils.upperCase(fileType)),
                          RundeckJobsImportMethod.valueOf(StringUtils.upperCase(importBehavior)));
    }

    /**
     * Import the definitions of jobs, from the given file, using the given behavior
     * 
     * @param filename of the file containing the jobs definitions - mandatory
     * @param fileType type of the file. See {@link FileType} - mandatory
     * @param importBehavior see {@link RundeckJobsImportMethod}
     * @return a {@link RundeckJobsImportResult} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the filename is blank (null, empty or whitespace), or the fileType is null
     * @throws IOException if we failed to read the file
     * @see #importJobs(InputStream, FileType, RundeckJobsImportMethod)
     */
    public RundeckJobsImportResult importJobs(String filename, FileType fileType, RundeckJobsImportMethod importBehavior)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException,
            IOException {
        AssertUtil.notBlank(filename, "filename (of jobs file) is mandatory to import jobs !");
        FileInputStream stream = null;
        try {
            stream = FileUtils.openInputStream(new File(filename));
            return importJobs(stream, fileType, importBehavior);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    /**
     * Import the definitions of jobs, from the given input stream
     * 
     * @param stream inputStream for reading the definitions - mandatory
     * @param fileType type of the file. See {@link FileType} - mandatory
     * @return a {@link RundeckJobsImportResult} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the stream is null, or the fileType is blank (null, empty or whitespace) or
     *             invalid
     * @see #importJobs(String, String)
     * @see #importJobs(InputStream, FileType, RundeckJobsImportMethod)
     */
    public RundeckJobsImportResult importJobs(InputStream stream, String fileType) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(fileType, "fileType is mandatory to import jobs !");
        return importJobs(stream, FileType.valueOf(StringUtils.upperCase(fileType)));
    }

    /**
     * Import the definitions of jobs, from the given input stream
     * 
     * @param stream inputStream for reading the definitions - mandatory
     * @param fileType type of the file. See {@link FileType} - mandatory
     * @return a {@link RundeckJobsImportResult} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the stream or fileType is null
     * @see #importJobs(String, FileType)
     * @see #importJobs(InputStream, FileType, RundeckJobsImportMethod)
     */
    public RundeckJobsImportResult importJobs(InputStream stream, FileType fileType) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return importJobs(stream, fileType, (RundeckJobsImportMethod) null);
    }

    /**
     * Import the definitions of jobs, from the given input stream, using the given behavior
     * 
     * @param stream inputStream for reading the definitions - mandatory
     * @param fileType type of the file. See {@link FileType} - mandatory
     * @param importBehavior see {@link RundeckJobsImportMethod}
     * @return a {@link RundeckJobsImportResult} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the stream is null, or the fileType is blank (null, empty or whitespace), or
     *             the fileType or behavior is not valid
     * @see #importJobs(String, String, String)
     * @see #importJobs(InputStream, FileType, RundeckJobsImportMethod)
     */
    public RundeckJobsImportResult importJobs(InputStream stream, String fileType, String importBehavior)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(fileType, "fileType is mandatory to import jobs !");
        return importJobs(stream,
                          FileType.valueOf(StringUtils.upperCase(fileType)),
                          RundeckJobsImportMethod.valueOf(StringUtils.upperCase(importBehavior)));
    }

    /**
     * Import the definitions of jobs, from the given input stream, using the given behavior
     * 
     * @param stream inputStream for reading the definitions - mandatory
     * @param fileType type of the file. See {@link FileType} - mandatory
     * @param importBehavior see {@link RundeckJobsImportMethod}
     * @return a {@link RundeckJobsImportResult} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the stream or fileType is null
     * @see #importJobs(String, FileType, RundeckJobsImportMethod)
     */
    public RundeckJobsImportResult importJobs(InputStream stream, FileType fileType,
            RundeckJobsImportMethod importBehavior) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notNull(stream, "inputStream of jobs is mandatory to import jobs !");
        AssertUtil.notNull(fileType, "fileType is mandatory to import jobs !");
        return new ApiCall(this, RundeckAPI.TWO).post(new ApiPathBuilder("/jobs/import").param("format", fileType)
                                                                        .param("dupeOption", importBehavior)
                                                                        .attach("xmlBatch", stream),
                                                      new JobsImportResultParser("result"));
    }

    /**
     * Find a job, identified by its project, group and name. Note that the groupPath is optional, as a job does not
     * need to belong to a group (either pass null, or an empty string).
     * 
     * @param project name of the project - mandatory
     * @param groupPath group to which the job belongs (if it belongs to a group) - optional
     * @param name of the job to find - mandatory
     * @return a {@link RundeckJob} instance - null if not found
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or the name is blank (null, empty or whitespace)
     * @see #getJob(String)
     */
    public RundeckJob findJob(String project, String groupPath, String name) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(project, "project is mandatory to find a job !");
        AssertUtil.notBlank(name, "job name is mandatory to find a job !");
        List<RundeckJob> jobs = getJobs(project, name, groupPath, new String[0]);
        return jobs.isEmpty() ? null : jobs.get(0);
    }

    /**
     * Get the definition of a single job, identified by the given ID
     * 
     * @param jobId identifier of the job - mandatory
     * @return a {@link RundeckJob} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #findJob(String, String, String)
     * @see #exportJob(String, String)
     */
    public RundeckJob getJob(String jobId) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(jobId, "jobId is mandatory to get the details of a job !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/job/", jobId), new JobParser("joblist/job"));
    }

    public void deleteJob(String jobId) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(jobId, "jobId is mandatory to delete a job !");
        try {
            new ApiCall(this, RundeckAPI.TWO).delete(new ApiPathBuilder("/job/", jobId), new StringParser("result/success/message"));
        } catch (Exception ex) {
        }

    }

    public void deleteProjectByName(final String projectName) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(projectName, "jobId is mandatory to delete a job !");
        try {
            new ApiCall(this, RundeckAPI.ELEVEN).delete(new ApiPathBuilder("/project/", projectName), new StringParser("result/success/message"));
        } catch (Exception ex) {
        }
    }

    /**
     * Trigger the execution of a RunDeck job (identified by the given ID), and return immediately (without waiting the
     * end of the job execution)
     * 
     * @param jobId identifier of the job - mandatory
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #triggerJob(String, Properties, Properties)
     * @see #runJob(String)
     */
    public RundeckExecution triggerJob(String jobId) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        return triggerJob(jobId, null);
    }

    /**
     * Trigger the execution of a RunDeck job (identified by the given ID), and return immediately (without waiting the
     * end of the job execution)
     * 
     * @param jobId identifier of the job - mandatory
     * @param options of the job - optional. See {@link OptionsBuilder}.
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #triggerJob(String, Properties, Properties)
     * @see #runJob(String, Properties)
     */
    public RundeckExecution triggerJob(String jobId, Properties options) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return triggerJob(jobId, options, null);
    }

    /**
     * Trigger the execution of a RunDeck job (identified by the given ID), and return immediately (without waiting the
     * end of the job execution)
     * 
     * @param jobId identifier of the job - mandatory
     * @param options of the job - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for overriding the nodes on which the job will be executed - optional. See
     *            {@link NodeFiltersBuilder}
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #triggerJob(String)
     * @see #runJob(String, Properties, Properties)
     */
    public RundeckExecution triggerJob(String jobId, Properties options, Properties nodeFilters)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(jobId, "jobId is mandatory to trigger a job !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/job/", jobId, "/run").param("argString",
                                                                                                      ParametersUtil.generateArgString(options))
                                                                               .nodeFilters(nodeFilters),
                                                     new ExecutionParser("result/executions/execution"));
    }

    /**
     * Run a RunDeck job (identified by the given ID), and wait until its execution is finished (or aborted) to return.
     * We will poll the RunDeck server at regular interval (every 5 seconds) to know if the execution is finished (or
     * aborted) or is still running.
     * 
     * @param jobId identifier of the job - mandatory
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #triggerJob(String)
     * @see #runJob(String, Properties, Properties, long, TimeUnit)
     */
    public RundeckExecution runJob(String jobId) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        return runJob(jobId, null);
    }

    /**
     * Run a RunDeck job (identified by the given ID), and wait until its execution is finished (or aborted) to return.
     * We will poll the RunDeck server at regular interval (every 5 seconds) to know if the execution is finished (or
     * aborted) or is still running.
     * 
     * @param jobId identifier of the job - mandatory
     * @param options of the job - optional. See {@link OptionsBuilder}.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #triggerJob(String, Properties)
     * @see #runJob(String, Properties, Properties, long, TimeUnit)
     */
    public RundeckExecution runJob(String jobId, Properties options) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return runJob(jobId, options, null);
    }

    /**
     * Run a RunDeck job (identified by the given ID), and wait until its execution is finished (or aborted) to return.
     * We will poll the RunDeck server at regular interval (every 5 seconds) to know if the execution is finished (or
     * aborted) or is still running.
     * 
     * @param jobId identifier of the job - mandatory
     * @param options of the job - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for overriding the nodes on which the job will be executed - optional. See
     *            {@link NodeFiltersBuilder}
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #triggerJob(String, Properties, Properties)
     * @see #runJob(String, Properties, Properties, long, TimeUnit)
     */
    public RundeckExecution runJob(String jobId, Properties options, Properties nodeFilters)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return runJob(jobId, options, nodeFilters, DEFAULT_POOLING_INTERVAL, DEFAULT_POOLING_UNIT);
    }

    /**
     * Run a RunDeck job (identified by the given ID), and wait until its execution is finished (or aborted) to return.
     * We will poll the RunDeck server at regular interval (configured by the poolingInterval/poolingUnit couple) to
     * know if the execution is finished (or aborted) or is still running.
     * 
     * @param jobId identifier of the job - mandatory
     * @param options of the job - optional. See {@link OptionsBuilder}.
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #triggerJob(String, Properties)
     * @see #runJob(String, Properties, Properties, long, TimeUnit)
     */
    public RundeckExecution runJob(String jobId, Properties options, long poolingInterval, TimeUnit poolingUnit)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return runJob(jobId, options, null, poolingInterval, poolingUnit);
    }

    /**
     * Run a RunDeck job (identified by the given ID), and wait until its execution is finished (or aborted) to return.
     * We will poll the RunDeck server at regular interval (configured by the poolingInterval/poolingUnit couple) to
     * know if the execution is finished (or aborted) or is still running.
     * 
     * @param jobId identifier of the job - mandatory
     * @param options of the job - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for overriding the nodes on which the job will be executed - optional. See
     *            {@link NodeFiltersBuilder}
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #triggerJob(String, Properties)
     * @see #runJob(String, Properties, Properties, long, TimeUnit)
     */
    public RundeckExecution runJob(String jobId, Properties options, Properties nodeFilters, long poolingInterval,
            TimeUnit poolingUnit) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException {
        if (poolingInterval <= 0) {
            poolingInterval = DEFAULT_POOLING_INTERVAL;
            poolingUnit = DEFAULT_POOLING_UNIT;
        }
        if (poolingUnit == null) {
            poolingUnit = DEFAULT_POOLING_UNIT;
        }

        RundeckExecution execution = triggerJob(jobId, options, nodeFilters);
        while (ExecutionStatus.RUNNING.equals(execution.getStatus())) {
            try {
                Thread.sleep(poolingUnit.toMillis(poolingInterval));
            } catch (InterruptedException e) {
                break;
            }
            execution = getExecution(execution.getId());
        }
        return execution;
    }

    /*
     * Ad-hoc commands
     */

    /**
     * Trigger the execution of an ad-hoc command, and return immediately (without waiting the end of the execution).
     * The command will not be dispatched to nodes, but be executed on the RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param command to be executed - mandatory
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or command is blank (null, empty or whitespace)
     * @see #triggerAdhocCommand(String, String, Properties, Integer, Boolean)
     * @see #runAdhocCommand(String, String)
     */
    public RundeckExecution triggerAdhocCommand(String project, String command) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return triggerAdhocCommand(project, command, null);
    }

    /**
     * Trigger the execution of an ad-hoc command, and return immediately (without waiting the end of the execution).
     * The command will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param command to be executed - mandatory
     * @param nodeFilters for selecting nodes on which the command will be executed. See {@link NodeFiltersBuilder}
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or command is blank (null, empty or whitespace)
     * @see #triggerAdhocCommand(String, String, Properties, Integer, Boolean)
     * @see #runAdhocCommand(String, String, Properties)
     */
    public RundeckExecution triggerAdhocCommand(String project, String command, Properties nodeFilters)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return triggerAdhocCommand(project, command, nodeFilters, null, null);
    }

    /**
     * Trigger the execution of an ad-hoc command, and return immediately (without waiting the end of the execution).
     * The command will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param command to be executed - mandatory
     * @param nodeFilters for selecting nodes on which the command will be executed. See {@link NodeFiltersBuilder}
     * @param nodeThreadcount thread count to use (for parallelizing when running on multiple nodes) - optional
     * @param nodeKeepgoing if true, continue executing on other nodes even if some fail - optional
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or command is blank (null, empty or whitespace)
     * @see #triggerAdhocCommand(String, String)
     * @see #runAdhocCommand(String, String, Properties)
     */
    public RundeckExecution triggerAdhocCommand(String project, String command, Properties nodeFilters,
            Integer nodeThreadcount, Boolean nodeKeepgoing) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(project, "project is mandatory to trigger an ad-hoc command !");
        AssertUtil.notBlank(command, "command is mandatory to trigger an ad-hoc command !");
        RundeckExecution execution = new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/run/command").param("project", project)
                                                                                             .param("exec", command)
                                                                                             .param("nodeThreadcount",
                                                                                                    nodeThreadcount)
                                                                                             .param("nodeKeepgoing",
                                                                                                    nodeKeepgoing)
                                                                                             .nodeFilters(nodeFilters),
                                                                           new ExecutionParser("result/execution"));
        // the first call just returns the ID of the execution, so we need another call to get a "real" execution
        return getExecution(execution.getId());
    }

    /**
     * Run an ad-hoc command, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The command will not be dispatched to nodes, but be executed on the RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param command to be executed - mandatory
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or command is blank (null, empty or whitespace)
     * @see #runAdhocCommand(String, String, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocCommand(String, String)
     */
    public RundeckExecution runAdhocCommand(String project, String command) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return runAdhocCommand(project, command, null);
    }

    /**
     * Run an ad-hoc command, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The command will not be dispatched to nodes, but be executed on the
     * RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param command to be executed - mandatory
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or command is blank (null, empty or whitespace)
     * @see #runAdhocCommand(String, String, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocCommand(String, String)
     */
    public RundeckExecution runAdhocCommand(String project, String command, long poolingInterval, TimeUnit poolingUnit)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return runAdhocCommand(project, command, null, poolingInterval, poolingUnit);
    }

    /**
     * Run an ad-hoc command, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The command will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param command to be executed - mandatory
     * @param nodeFilters for selecting nodes on which the command will be executed. See {@link NodeFiltersBuilder}
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or command is blank (null, empty or whitespace)
     * @see #runAdhocCommand(String, String, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocCommand(String, String, Properties)
     */
    public RundeckExecution runAdhocCommand(String project, String command, Properties nodeFilters)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return runAdhocCommand(project, command, nodeFilters, null, null);
    }

    /**
     * Run an ad-hoc command, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The command will be dispatched to nodes, accordingly to the
     * nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param command to be executed - mandatory
     * @param nodeFilters for selecting nodes on which the command will be executed. See {@link NodeFiltersBuilder}
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or command is blank (null, empty or whitespace)
     * @see #runAdhocCommand(String, String, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocCommand(String, String, Properties)
     */
    public RundeckExecution runAdhocCommand(String project, String command, Properties nodeFilters,
            long poolingInterval, TimeUnit poolingUnit) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        return runAdhocCommand(project, command, nodeFilters, null, null, poolingInterval, poolingUnit);
    }

    /**
     * Run an ad-hoc command, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The command will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param command to be executed - mandatory
     * @param nodeFilters for selecting nodes on which the command will be executed. See {@link NodeFiltersBuilder}
     * @param nodeThreadcount thread count to use (for parallelizing when running on multiple nodes) - optional
     * @param nodeKeepgoing if true, continue executing on other nodes even if some fail - optional
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or command is blank (null, empty or whitespace)
     * @see #runAdhocCommand(String, String, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocCommand(String, String, Properties, Integer, Boolean)
     */
    public RundeckExecution runAdhocCommand(String project, String command, Properties nodeFilters,
            Integer nodeThreadcount, Boolean nodeKeepgoing) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        return runAdhocCommand(project,
                               command,
                               nodeFilters,
                               nodeThreadcount,
                               nodeKeepgoing,
                               DEFAULT_POOLING_INTERVAL,
                               DEFAULT_POOLING_UNIT);
    }

    /**
     * Run an ad-hoc command, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The command will be dispatched to nodes, accordingly to the
     * nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param command to be executed - mandatory
     * @param nodeFilters for selecting nodes on which the command will be executed. See {@link NodeFiltersBuilder}
     * @param nodeThreadcount thread count to use (for parallelizing when running on multiple nodes) - optional
     * @param nodeKeepgoing if true, continue executing on other nodes even if some fail - optional
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or command is blank (null, empty or whitespace)
     * @see #triggerAdhocCommand(String, String, Properties, Integer, Boolean)
     */
    public RundeckExecution runAdhocCommand(String project, String command, Properties nodeFilters,
            Integer nodeThreadcount, Boolean nodeKeepgoing, long poolingInterval, TimeUnit poolingUnit)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        if (poolingInterval <= 0) {
            poolingInterval = DEFAULT_POOLING_INTERVAL;
            poolingUnit = DEFAULT_POOLING_UNIT;
        }
        if (poolingUnit == null) {
            poolingUnit = DEFAULT_POOLING_UNIT;
        }

        RundeckExecution execution = triggerAdhocCommand(project, command, nodeFilters, nodeThreadcount, nodeKeepgoing);
        while (ExecutionStatus.RUNNING.equals(execution.getStatus())) {
            try {
                Thread.sleep(poolingUnit.toMillis(poolingInterval));
            } catch (InterruptedException e) {
                break;
            }
            execution = getExecution(execution.getId());
        }
        return execution;
    }

    /*
     * Ad-hoc scripts
     */

    /**
     * Trigger the execution of an ad-hoc script, and return immediately (without waiting the end of the execution). The
     * script will not be dispatched to nodes, but be executed on the RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #triggerAdhocScript(String, String, Properties, Properties, Integer, Boolean)
     * @see #runAdhocScript(String, String)
     */
    public RundeckExecution triggerAdhocScript(String project, String scriptFilename) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        return triggerAdhocScript(project, scriptFilename, null);
    }

    /**
     * Trigger the execution of an ad-hoc script, and return immediately (without waiting the end of the execution). The
     * script will not be dispatched to nodes, but be executed on the RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #triggerAdhocScript(String, String, Properties, Properties, Integer, Boolean)
     * @see #runAdhocScript(String, String, Properties)
     */
    public RundeckExecution triggerAdhocScript(String project, String scriptFilename, Properties options)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException,
            IOException {
        return triggerAdhocScript(project, scriptFilename, options, null);
    }

    /**
     * Trigger the execution of an ad-hoc script, and return immediately (without waiting the end of the execution). The
     * script will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the command will be executed. See {@link NodeFiltersBuilder}
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #triggerAdhocScript(String, String, Properties, Properties, Integer, Boolean)
     * @see #runAdhocScript(String, String, Properties, Properties)
     */
    public RundeckExecution triggerAdhocScript(String project, String scriptFilename, Properties options,
            Properties nodeFilters) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException, IOException {
        return triggerAdhocScript(project, scriptFilename, options, nodeFilters, null, null);
    }

    /**
     * Trigger the execution of an ad-hoc script, and return immediately (without waiting the end of the execution). The
     * script will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the command will be executed. See {@link NodeFiltersBuilder}
     * @param nodeThreadcount thread count to use (for parallelizing when running on multiple nodes) - optional
     * @param nodeKeepgoing if true, continue executing on other nodes even if some fail - optional
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #triggerAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean)
     * @see #runAdhocScript(String, String, Properties, Properties, Integer, Boolean, long, TimeUnit)
     */
    public RundeckExecution triggerAdhocScript(String project, String scriptFilename, Properties options,
            Properties nodeFilters, Integer nodeThreadcount, Boolean nodeKeepgoing) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        AssertUtil.notBlank(scriptFilename, "scriptFilename is mandatory to trigger an ad-hoc script !");
        FileInputStream stream = null;
        try {
            stream = FileUtils.openInputStream(new File(scriptFilename));
            return triggerAdhocScript(project, stream, options, nodeFilters, nodeThreadcount, nodeKeepgoing);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    /**
     * Trigger the execution of an ad-hoc script, and return immediately (without waiting the end of the execution). The
     * script will not be dispatched to nodes, but be executed on the RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @see #triggerAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean)
     * @see #runAdhocScript(String, InputStream)
     */
    public RundeckExecution triggerAdhocScript(String project, InputStream script) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return triggerAdhocScript(project, script, null);
    }

    /**
     * Trigger the execution of an ad-hoc script, and return immediately (without waiting the end of the execution). The
     * script will not be dispatched to nodes, but be executed on the RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @see #triggerAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean)
     * @see #runAdhocScript(String, InputStream, Properties)
     */
    public RundeckExecution triggerAdhocScript(String project, InputStream script, Properties options)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return triggerAdhocScript(project, script, options, null);
    }

    /**
     * Trigger the execution of an ad-hoc script, and return immediately (without waiting the end of the execution). The
     * script will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the command will be executed. See {@link NodeFiltersBuilder}
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @see #triggerAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean)
     * @see #runAdhocScript(String, InputStream, Properties, Properties)
     */
    public RundeckExecution triggerAdhocScript(String project, InputStream script, Properties options,
            Properties nodeFilters) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException {
        return triggerAdhocScript(project, script, options, nodeFilters, null, null);
    }

    /**
     * Trigger the execution of an ad-hoc script, and return immediately (without waiting the end of the execution). The
     * script will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the command will be executed. See {@link NodeFiltersBuilder}
     * @param nodeThreadcount thread count to use (for parallelizing when running on multiple nodes) - optional
     * @param nodeKeepgoing if true, continue executing on other nodes even if some fail - optional
     * @return a {@link RundeckExecution} instance for the newly created (and running) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @see #triggerAdhocScript(String, String, Properties, Properties, Integer, Boolean)
     * @see #runAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean, long, TimeUnit)
     */
    public RundeckExecution triggerAdhocScript(String project, InputStream script, Properties options,
            Properties nodeFilters, Integer nodeThreadcount, Boolean nodeKeepgoing) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(project, "project is mandatory to trigger an ad-hoc script !");
        AssertUtil.notNull(script, "script is mandatory to trigger an ad-hoc script !");
        RundeckExecution execution = new ApiCall(this, RundeckAPI.TWO).post(new ApiPathBuilder("/run/script").param("project", project)
                                                                                             .attach("scriptFile",
                                                                                                     script)
                                                                                             .param("argString",
                                                                                                    ParametersUtil.generateArgString(options))
                                                                                             .param("nodeThreadcount",
                                                                                                    nodeThreadcount)
                                                                                             .param("nodeKeepgoing",
                                                                                                    nodeKeepgoing)
                                                                                             .nodeFilters(nodeFilters),
                                                                            new ExecutionParser("result/execution"));
        // the first call just returns the ID of the execution, so we need another call to get a "real" execution
        return getExecution(execution.getId());
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The script will not be dispatched to nodes, but be executed on the RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, String, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, String)
     */
    public RundeckExecution runAdhocScript(String project, String scriptFilename) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        return runAdhocScript(project, scriptFilename, null);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The script will not be dispatched to nodes, but be executed on the
     * RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, String, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, String)
     */
    public RundeckExecution runAdhocScript(String project, String scriptFilename, long poolingInterval,
            TimeUnit poolingUnit) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException, IOException {
        return runAdhocScript(project, scriptFilename, null, poolingInterval, poolingUnit);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The script will not be dispatched to nodes, but be executed on the RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, String, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, String, Properties)
     */
    public RundeckExecution runAdhocScript(String project, String scriptFilename, Properties options)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException,
            IOException {
        return runAdhocScript(project, scriptFilename, options, null);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The script will not be dispatched to nodes, but be executed on the
     * RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, String, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, String, Properties)
     */
    public RundeckExecution runAdhocScript(String project, String scriptFilename, Properties options,
            long poolingInterval, TimeUnit poolingUnit) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException, IOException {
        return runAdhocScript(project, scriptFilename, options, null, poolingInterval, poolingUnit);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The script will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the script will be executed. See {@link NodeFiltersBuilder}
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, String, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, String, Properties, Properties)
     */
    public RundeckExecution runAdhocScript(String project, String scriptFilename, Properties options,
            Properties nodeFilters) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException, IOException {
        return runAdhocScript(project, scriptFilename, options, nodeFilters, null, null);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The script will be dispatched to nodes, accordingly to the nodeFilters
     * parameter.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the script will be executed. See {@link NodeFiltersBuilder}
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, String, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, String, Properties, Properties)
     */
    public RundeckExecution runAdhocScript(String project, String scriptFilename, Properties options,
            Properties nodeFilters, long poolingInterval, TimeUnit poolingUnit) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        return runAdhocScript(project, scriptFilename, options, nodeFilters, null, null, poolingInterval, poolingUnit);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The script will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the script will be executed. See {@link NodeFiltersBuilder}
     * @param nodeThreadcount thread count to use (for parallelizing when running on multiple nodes) - optional
     * @param nodeKeepgoing if true, continue executing on other nodes even if some fail - optional
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, String, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, String, Properties, Properties, Integer, Boolean)
     */
    public RundeckExecution runAdhocScript(String project, String scriptFilename, Properties options,
            Properties nodeFilters, Integer nodeThreadcount, Boolean nodeKeepgoing) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        return runAdhocScript(project,
                              scriptFilename,
                              options,
                              nodeFilters,
                              nodeThreadcount,
                              nodeKeepgoing,
                              DEFAULT_POOLING_INTERVAL,
                              DEFAULT_POOLING_UNIT);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The script will be dispatched to nodes, accordingly to the nodeFilters
     * parameter.
     * 
     * @param project name of the project - mandatory
     * @param scriptFilename filename of the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the script will be executed. See {@link NodeFiltersBuilder}
     * @param nodeThreadcount thread count to use (for parallelizing when running on multiple nodes) - optional
     * @param nodeKeepgoing if true, continue executing on other nodes even if some fail - optional
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project or scriptFilename is blank (null, empty or whitespace)
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, String, Properties, Properties, Integer, Boolean)
     */
    public RundeckExecution runAdhocScript(String project, String scriptFilename, Properties options,
            Properties nodeFilters, Integer nodeThreadcount, Boolean nodeKeepgoing, long poolingInterval,
            TimeUnit poolingUnit) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException, IOException {
        AssertUtil.notBlank(scriptFilename, "scriptFilename is mandatory to run an ad-hoc script !");
        FileInputStream stream = null;
        try {
            stream = FileUtils.openInputStream(new File(scriptFilename));
            return runAdhocScript(project,
                                  stream,
                                  options,
                                  nodeFilters,
                                  nodeThreadcount,
                                  nodeKeepgoing,
                                  poolingInterval,
                                  poolingUnit);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The script will not be dispatched to nodes, but be executed on the RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, InputStream)
     */
    public RundeckExecution runAdhocScript(String project, InputStream script) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        return runAdhocScript(project, script, null);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The script will not be dispatched to nodes, but be executed on the
     * RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, InputStream)
     */
    public RundeckExecution runAdhocScript(String project, InputStream script, long poolingInterval,
            TimeUnit poolingUnit) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException, IOException {
        return runAdhocScript(project, script, null, poolingInterval, poolingUnit);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The script will not be dispatched to nodes, but be executed on the RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, InputStream, Properties)
     */
    public RundeckExecution runAdhocScript(String project, InputStream script, Properties options)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException,
            IOException {
        return runAdhocScript(project, script, options, null);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The script will not be dispatched to nodes, but be executed on the
     * RunDeck server.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, InputStream, Properties)
     */
    public RundeckExecution runAdhocScript(String project, InputStream script, Properties options,
            long poolingInterval, TimeUnit poolingUnit) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException, IOException {
        return runAdhocScript(project, script, options, null, poolingInterval, poolingUnit);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The script will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the script will be executed. See {@link NodeFiltersBuilder}
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, InputStream, Properties, Properties)
     */
    public RundeckExecution runAdhocScript(String project, InputStream script, Properties options,
            Properties nodeFilters) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException, IOException {
        return runAdhocScript(project, script, options, nodeFilters, null, null);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The script will be dispatched to nodes, accordingly to the nodeFilters
     * parameter.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the script will be executed. See {@link NodeFiltersBuilder}
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, InputStream, Properties, Properties)
     */
    public RundeckExecution runAdhocScript(String project, InputStream script, Properties options,
            Properties nodeFilters, long poolingInterval, TimeUnit poolingUnit) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        return runAdhocScript(project, script, options, nodeFilters, null, null, poolingInterval, poolingUnit);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (every 5 seconds) to know if the execution is finished (or aborted) or is still
     * running. The script will be dispatched to nodes, accordingly to the nodeFilters parameter.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the script will be executed. See {@link NodeFiltersBuilder}
     * @param nodeThreadcount thread count to use (for parallelizing when running on multiple nodes) - optional
     * @param nodeKeepgoing if true, continue executing on other nodes even if some fail - optional
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean)
     */
    public RundeckExecution runAdhocScript(String project, InputStream script, Properties options,
            Properties nodeFilters, Integer nodeThreadcount, Boolean nodeKeepgoing) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException, IOException {
        return runAdhocScript(project,
                              script,
                              options,
                              nodeFilters,
                              nodeThreadcount,
                              nodeKeepgoing,
                              DEFAULT_POOLING_INTERVAL,
                              DEFAULT_POOLING_UNIT);
    }

    /**
     * Run an ad-hoc script, and wait until its execution is finished (or aborted) to return. We will poll the RunDeck
     * server at regular interval (configured by the poolingInterval/poolingUnit couple) to know if the execution is
     * finished (or aborted) or is still running. The script will be dispatched to nodes, accordingly to the nodeFilters
     * parameter.
     * 
     * @param project name of the project - mandatory
     * @param script inputStream for reading the script to be executed - mandatory
     * @param options of the script - optional. See {@link OptionsBuilder}.
     * @param nodeFilters for selecting nodes on which the script will be executed. See {@link NodeFiltersBuilder}
     * @param nodeThreadcount thread count to use (for parallelizing when running on multiple nodes) - optional
     * @param nodeKeepgoing if true, continue executing on other nodes even if some fail - optional
     * @param poolingInterval for checking the status of the execution. Must be > 0.
     * @param poolingUnit unit (seconds, milli-seconds, ...) of the interval. Default to seconds.
     * @return a {@link RundeckExecution} instance for the (finished/aborted) execution - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace) or the script is null
     * @throws IOException if we failed to read the file
     * @see #runAdhocScript(String, String, Properties, Properties, Integer, Boolean, long, TimeUnit)
     * @see #triggerAdhocScript(String, InputStream, Properties, Properties, Integer, Boolean)
     */
    public RundeckExecution runAdhocScript(String project, InputStream script, Properties options,
            Properties nodeFilters, Integer nodeThreadcount, Boolean nodeKeepgoing, long poolingInterval,
            TimeUnit poolingUnit) throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException,
            IllegalArgumentException {
        if (poolingInterval <= 0) {
            poolingInterval = DEFAULT_POOLING_INTERVAL;
            poolingUnit = DEFAULT_POOLING_UNIT;
        }
        if (poolingUnit == null) {
            poolingUnit = DEFAULT_POOLING_UNIT;
        }

        RundeckExecution execution = triggerAdhocScript(project,
                                                        script,
                                                        options,
                                                        nodeFilters,
                                                        nodeThreadcount,
                                                        nodeKeepgoing);
        while (ExecutionStatus.RUNNING.equals(execution.getStatus())) {
            try {
                Thread.sleep(poolingUnit.toMillis(poolingInterval));
            } catch (InterruptedException e) {
                break;
            }
            execution = getExecution(execution.getId());
        }
        return execution;
    }

    /*
     * Executions
     */

    /**
     * Get all running executions (for all projects)
     * 
     * @return a {@link List} of {@link RundeckExecution} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @see #getRunningExecutions(String)
     */
    public List<RundeckExecution> getRunningExecutions() throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException {
        List<RundeckExecution> executions = new ArrayList<RundeckExecution>();
        for (RundeckProject project : getProjects()) {
            executions.addAll(getRunningExecutions(project.getName()));
        }
        return executions;
    }

    /**
     * Get the running executions for the given project
     * 
     * @param project name of the project - mandatory
     * @return a {@link List} of {@link RundeckExecution} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getRunningExecutions()
     */
    public List<RundeckExecution> getRunningExecutions(String project) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(project, "project is mandatory get all running executions !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/executions/running").param("project", project),
                                                     new ListParser<RundeckExecution>(new ExecutionParser(),
                                                                      "result/executions/execution"));
    }

    /**
     * Get the executions of the given job
     * 
     * @param jobId identifier of the job - mandatory
     * @return a {@link List} of {@link RundeckExecution} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #getJobExecutions(String, RundeckExecution.ExecutionStatus, Long, Long)
     */
    public List<RundeckExecution> getJobExecutions(String jobId) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        return getJobExecutions(jobId, (ExecutionStatus) null);
    }

    /**
     * Get the executions of the given job
     * 
     * @param jobId identifier of the job - mandatory
     * @param status of the executions, see {@link ExecutionStatus} - optional (null for all)
     * @return a {@link List} of {@link RundeckExecution} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace), or the executionStatus is
     *             invalid
     * @see #getJobExecutions(String, String, Long, Long)
     */
    public List<RundeckExecution> getJobExecutions(String jobId, String status) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return getJobExecutions(jobId,
                                StringUtils.isBlank(status) ? null : ExecutionStatus.valueOf(StringUtils.upperCase(status)));
    }

    /**
     * Get the executions of the given job
     * 
     * @param jobId identifier of the job - mandatory
     * @param status of the executions, see {@link ExecutionStatus} - optional (null for all)
     * @return a {@link List} of {@link RundeckExecution} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     * @see #getJobExecutions(String, RundeckExecution.ExecutionStatus, Long, Long)
     */
    public List<RundeckExecution> getJobExecutions(String jobId, ExecutionStatus status) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return getJobExecutions(jobId, status, null, null);
    }

    /**
     * Get the executions of the given job
     * 
     * @param jobId identifier of the job - mandatory
     * @param status of the executions, see {@link ExecutionStatus} - optional (null for all)
     * @param max number of results to return - optional (null for all)
     * @param offset the 0-indexed offset for the first result to return - optional
     * @return a {@link List} of {@link RundeckExecution} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace), or the executionStatus is
     *             invalid
     * @see #getJobExecutions(String, RundeckExecution.ExecutionStatus, Long, Long)
     */
    public List<RundeckExecution> getJobExecutions(String jobId, String status, Long max, Long offset)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return getJobExecutions(jobId,
                                StringUtils.isBlank(status) ? null : ExecutionStatus.valueOf(StringUtils.upperCase(status)),
                                max,
                                offset);
    }

    /**
     * Get the executions of the given job
     * 
     * @param jobId identifier of the job - mandatory
     * @param status of the executions, see {@link ExecutionStatus} - optional (null for all)
     * @param max number of results to return - optional (null for all)
     * @param offset the 0-indexed offset for the first result to return - optional
     * @return a {@link List} of {@link RundeckExecution} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent job with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the jobId is blank (null, empty or whitespace)
     */
    public List<RundeckExecution> getJobExecutions(String jobId, ExecutionStatus status, Long max, Long offset)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(jobId, "jobId is mandatory to get the executions of a job !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/job/", jobId, "/executions").param("status", status)
                                                                                      .param("max", max)
                                                                                      .param("offset", offset),
                                                     new ListParser<RundeckExecution>(new ExecutionParser(),
                                                                      "result/executions/execution"));
    }

    /**
     * Get a single execution, identified by the given ID
     * 
     * @param executionId identifier of the execution - mandatory
     * @return a {@link RundeckExecution} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent execution with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the executionId is null
     */
    public RundeckExecution getExecution(Long executionId) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notNull(executionId, "executionId is mandatory to get the details of an execution !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/execution/", executionId.toString()),
                                                     new ExecutionParser("result/executions/execution"));
    }

    /**
     * Abort an execution (identified by the given ID). The execution should be running...
     * 
     * @param executionId identifier of the execution - mandatory
     * @return a {@link RundeckAbort} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent execution with this ID)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the executionId is null
     */
    public RundeckAbort abortExecution(Long executionId) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notNull(executionId, "executionId is mandatory to abort an execution !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/execution/", executionId.toString(), "/abort"),
                                                     new AbortParser("result/abort"));
    }

    /*
     * History
     */

    /**
     * Get the (events) history for the given project
     * 
     * @param project name of the project - mandatory
     * @return a {@link RundeckHistory} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getHistory(String, String, String, String, String, Date, Date, Long, Long)
     */
    public RundeckHistory getHistory(String project) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        return getHistory(project, null, null, null, null, null, null, null, null);
    }

    /**
     * Get the (events) history for the given project
     * 
     * @param project name of the project - mandatory
     * @param max number of results to return - optional (default to 20)
     * @param offset the 0-indexed offset for the first result to return - optional (default to O)
     * @return a {@link RundeckHistory} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getHistory(String, String, String, String, String, Date, Date, Long, Long)
     */
    public RundeckHistory getHistory(String project, Long max, Long offset) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return getHistory(project, null, null, null, null, null, null, max, offset);
    }

    /**
     * Get the (events) history for the given project
     * 
     * @param project name of the project - mandatory
     * @param jobId include only events matching the given job ID - optional
     * @param reportId include only events matching the given report ID - optional
     * @param user include only events created by the given user - optional
     * @return a {@link RundeckHistory} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getHistory(String, String, String, String, String, Date, Date, Long, Long)
     */
    public RundeckHistory getHistory(String project, String jobId, String reportId, String user)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return getHistory(project, jobId, reportId, user, null, null, null, null, null);
    }

    /**
     * Get the (events) history for the given project
     * 
     * @param project name of the project - mandatory
     * @param jobId include only events matching the given job ID - optional
     * @param reportId include only events matching the given report ID - optional
     * @param user include only events created by the given user - optional
     * @param max number of results to return - optional (default to 20)
     * @param offset the 0-indexed offset for the first result to return - optional (default to O)
     * @return a {@link RundeckHistory} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getHistory(String, String, String, String, String, Date, Date, Long, Long)
     */
    public RundeckHistory getHistory(String project, String jobId, String reportId, String user, Long max, Long offset)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return getHistory(project, jobId, reportId, user, null, null, null, max, offset);
    }

    /**
     * Get the (events) history for the given project
     * 
     * @param project name of the project - mandatory
     * @param recent include only events matching the given period of time. Format : "XY", where X is an integer, and Y
     *            is one of : "h" (hour), "d" (day), "w" (week), "m" (month), "y" (year). Example : "2w" (= last 2
     *            weeks), "5d" (= last 5 days), etc. Optional.
     * @return a {@link RundeckHistory} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getHistory(String, String, String, String, String, Date, Date, Long, Long)
     */
    public RundeckHistory getHistory(String project, String recent) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return getHistory(project, null, null, null, recent, null, null, null, null);
    }

    /**
     * Get the (events) history for the given project
     * 
     * @param project name of the project - mandatory
     * @param recent include only events matching the given period of time. Format : "XY", where X is an integer, and Y
     *            is one of : "h" (hour), "d" (day), "w" (week), "m" (month), "y" (year). Example : "2w" (= last 2
     *            weeks), "5d" (= last 5 days), etc. Optional.
     * @param max number of results to return - optional (default to 20)
     * @param offset the 0-indexed offset for the first result to return - optional (default to O)
     * @return a {@link RundeckHistory} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getHistory(String, String, String, String, String, Date, Date, Long, Long)
     */
    public RundeckHistory getHistory(String project, String recent, Long max, Long offset) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return getHistory(project, null, null, null, recent, null, null, max, offset);
    }

    /**
     * Get the (events) history for the given project
     * 
     * @param project name of the project - mandatory
     * @param begin date for the earlier events to retrieve - optional
     * @param end date for the latest events to retrieve - optional
     * @return a {@link RundeckHistory} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getHistory(String, String, String, String, String, Date, Date, Long, Long)
     */
    public RundeckHistory getHistory(String project, Date begin, Date end) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return getHistory(project, null, null, null, null, begin, end, null, null);
    }

    /**
     * Get the (events) history for the given project
     * 
     * @param project name of the project - mandatory
     * @param begin date for the earlier events to retrieve - optional
     * @param end date for the latest events to retrieve - optional
     * @param max number of results to return - optional (default to 20)
     * @param offset the 0-indexed offset for the first result to return - optional (default to O)
     * @return a {@link RundeckHistory} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getHistory(String, String, String, String, String, Date, Date, Long, Long)
     */
    public RundeckHistory getHistory(String project, Date begin, Date end, Long max, Long offset)
            throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        return getHistory(project, null, null, null, null, begin, end, max, offset);
    }

    /**
     * Get the (events) history for the given project
     * 
     * @param project name of the project - mandatory
     * @param jobId include only events matching the given job ID - optional
     * @param reportId include only events matching the given report ID - optional
     * @param user include only events created by the given user - optional
     * @param recent include only events matching the given period of time. Format : "XY", where X is an integer, and Y
     *            is one of : "h" (hour), "d" (day), "w" (week), "m" (month), "y" (year). Example : "2w" (= last 2
     *            weeks), "5d" (= last 5 days), etc. Optional.
     * @param begin date for the earlier events to retrieve - optional
     * @param end date for the latest events to retrieve - optional
     * @param max number of results to return - optional (default to 20)
     * @param offset the 0-indexed offset for the first result to return - optional (default to O)
     * @return a {@link RundeckHistory} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     */
    public RundeckHistory getHistory(String project, String jobId, String reportId, String user, String recent,
            Date begin, Date end, Long max, Long offset) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(project, "project is mandatory to get the history !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/history").param("project", project)
                                                                   .param("jobIdFilter", jobId)
                                                                   .param("reportIdFilter", reportId)
                                                                   .param("userFilter", user)
                                                                   .param("recentFilter", recent)
                                                                   .param("begin", begin)
                                                                   .param("end", end)
                                                                   .param("max", max)
                                                                   .param("offset", offset),
                                                     new HistoryParser("result/events"));
    }

    /*
     * Nodes
     */

    /**
     * List all nodes (for all projects)
     * 
     * @return a {@link List} of {@link RundeckNode} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     */
    public List<RundeckNode> getNodes() throws RundeckApiException, RundeckApiLoginException, RundeckApiTokenException {
        List<RundeckNode> nodes = new ArrayList<RundeckNode>();
        for (RundeckProject project : getProjects()) {
            nodes.addAll(getNodes(project.getName()));
        }
        return nodes;
    }

    /**
     * List all nodes that belongs to the given project
     * 
     * @param project name of the project - mandatory
     * @return a {@link List} of {@link RundeckNode} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     * @see #getNodes(String, Properties)
     */
    public List<RundeckNode> getNodes(String project) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        return getNodes(project, null);
    }

    /**
     * List nodes that belongs to the given project
     * 
     * @param project name of the project - mandatory
     * @param nodeFilters for filtering the nodes - optional. See {@link NodeFiltersBuilder}
     * @return a {@link List} of {@link RundeckNode} : might be empty, but won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the project is blank (null, empty or whitespace)
     */
    public List<RundeckNode> getNodes(String project, Properties nodeFilters) throws RundeckApiException,
            RundeckApiLoginException, RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(project, "project is mandatory to get all nodes !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/resources").param("project", project)
                                                                     .nodeFilters(nodeFilters),
                                                     new ListParser<RundeckNode>(new NodeParser(), "project/node"));
    }

    /**
     * Get the definition of a single node
     * 
     * @param name of the node - mandatory
     * @param project name of the project - mandatory
     * @return a {@link RundeckNode} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API (non-existent name or project with this name)
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     * @throws IllegalArgumentException if the name or project is blank (null, empty or whitespace)
     */
    public RundeckNode getNode(String name, String project) throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException, IllegalArgumentException {
        AssertUtil.notBlank(name, "the name of the node is mandatory to get a node !");
        AssertUtil.notBlank(project, "project is mandatory to get a node !");
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/resource/", name).param("project", project),
                                                     new NodeParser("project/node"));
    }

    /*
     * System Info
     */

    /**
     * Get system informations about the RunDeck server
     * 
     * @return a {@link RundeckSystemInfo} instance - won't be null
     * @throws RundeckApiException in case of error when calling the API
     * @throws RundeckApiLoginException if the login fails (in case of login-based authentication)
     * @throws RundeckApiTokenException if the token is invalid (in case of token-based authentication)
     */
    public RundeckSystemInfo getSystemInfo() throws RundeckApiException, RundeckApiLoginException,
            RundeckApiTokenException {
        return new ApiCall(this, RundeckAPI.TWO).get(new ApiPathBuilder("/system/info"), new SystemInfoParser("result/system"));
    }

    /**
     * @return the URL of the RunDeck instance ("http://localhost:4440", "http://rundeck.your-compagny.com/", etc)
     */
    public String getUrl() {
        return url;
    }

    /**
     * @return the auth-token used for authentication on the RunDeck instance (null if using login-based auth)
     */
    public String getToken() {
        return token;
    }

    /**
     * @return the login used for authentication on the RunDeck instance (null if using token-based auth)
     */
    public String getLogin() {
        return login;
    }

    /**
     * @return the password used for authentication on the RunDeck instance (null if using token-based auth)
     */
    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("RundeckClient ");
        str.append(" [").append(url).append("] ");
        if (token != null) {
            str.append("(token=").append(token).append(")");
        } else {
            str.append("(credentials=").append(login).append("|").append(password).append(")");
        }
        return str.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((login == null) ? 0 : login.hashCode());
        result = prime * result + ((password == null) ? 0 : password.hashCode());
        result = prime * result + ((token == null) ? 0 : token.hashCode());
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RundeckClient other = (RundeckClient) obj;
        if (login == null) {
            if (other.login != null)
                return false;
        } else if (!login.equals(other.login))
            return false;
        if (password == null) {
            if (other.password != null)
                return false;
        } else if (!password.equals(other.password))
            return false;
        if (token == null) {
            if (other.token != null)
                return false;
        } else if (!token.equals(other.token))
            return false;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        return true;
    }

    public void createProject(final String projectName) {
        final RundeckProject rundeckProject = new RundeckProject();
        rundeckProject.setName(projectName);

        final String rundeckProjectAsJson = GSON.toJson(rundeckProject);

        try {
            final HttpPost httpPost = new HttpPost(url + RundeckAPI.ELEVEN.getEndpoint() + "/projects");
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(rundeckProjectAsJson));

            new ApiCall(this, RundeckAPI.ELEVEN).execute(httpPost, null);
        } catch (Exception ex) {
            System.err.println(format("while creating %s", projectName));
            ex.printStackTrace(System.err);
        }
    }

    public void importJobsToProject(final File joblist,
                                    final String projectName) {
        final FileType fileType = FileType.XML;
        try {
            new ApiCall(this, RundeckAPI.FOURTEEN).post(new ApiPathBuilder(format("/project/%s/jobs/import", projectName))
                                                                .param("format", fileType)
                                                                .attach("xmlBatch", new FileInputStream(joblist)),
                                                        new JobsImportResultParser("result"));
        } catch (final Exception ex) {
            System.err.println(format("while importing %s", projectName));
            ex.printStackTrace(System.err);
        }
    }


}
