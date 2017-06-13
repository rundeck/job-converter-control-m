package org.rundeck.controlm

import org.rundeck.api.RundeckClient
import org.rundeck.api.domain.RundeckProject

class Uploader extends Base {

    static RundeckClient getRundeckClient(final Properties properties) {
        return new RundeckClient(properties['upload.rundeck'],
                properties['upload.username'],
                properties['upload.password']);
    }

    static File getInputFile(args) {
        final String inputFile = args.length == 1 ? args[0] : null;
        if (!inputFile) {
            printlnAndExit "you need to specify a directory of projects as argument to this command"
        }

        final File file = new File(inputFile)
        if (!file.exists()) {
            printlnAndExit "$inputFile must exists"
        }

        return file;
    }

    static List<File> getProjects(final File inputFile) {
        if (inputFile.isDirectory()) {
            final FilenameFilter endsWithXML = { dir, filename ->
                return filename.endsWith('.xml');
            }

            return inputFile.listFiles(endsWithXML) as List
        }

        return Collections.singletonList(inputFile);
    }

    public static void main(String... args) {
        final Properties config = getProperties();
        final RundeckClient rundeck = getRundeckClient(config);

        final File inputFile = getInputFile(args);
        final List<File> projects = getProjects(inputFile);
        if (!projects) {
            return
        }

        final boolean deleteProjects = Boolean.parseBoolean(config['upload.delete.projects'] ?: 'false')
        if (deleteProjects) {
            // delete existing projects
            rundeck.getProjects().forEach({ RundeckProject project ->
                final String projectName = project.name
                println "deleting project: ${projectName}";
                rundeck.deleteProjectByName(projectName);
            })

            // create new projects
            projects.forEach({ final File project ->
                final String projectName = project.name.replaceAll(/\.xml/, "")
                println "creating project: ${projectName}";

                rundeck.createProject(projectName);
            })
        }

        // create new projects
        projects.forEach({ final File project ->
            final String projectName = project.name.replaceAll(/\.xml/, "")
            println "importing project: ${projectName}";
            rundeck.importJobsToProject(project, projectName);
        })

    }

}



