# myAemProject
A AEM project for learning


To create a project
mvn org.apache.maven.plugins:maven-archetype-plugin:2.4:generate -DarchetypeRepository=http://repo.adobe.com/nexus/content/groups/public/  -DarchetypeGroupId=com.day.jcr.vault -DarchetypeArtifactId=multimodule-content-package-archetype -DarchetypeVersion=1.0.2 -DgroupId=aemProject  -DartifactId=myAemProject -Dversion=1.0-SNAPSHOT -Dpackage=com.us.aem.project  -DappsFolderName=myAemProject  -DartifactName="my AEM project"   -DcqVersion="6.1"  -DpackageGroup="com.us.aem.project"


my AEM project
========

This a content package project generated using the multimodule-content-package-archetype.

Building
--------

This project uses Maven for building. Common commands:

From the root directory, run ``mvn -PautoInstallPackage clean install`` to build the bundle and content package and install to a CQ instance.

From the bundle directory, run ``mvn -PautoInstallBundle clean install`` to build *just* the bundle and install to a CQ instance.

Using with VLT
--------------

To use vlt with this project, first build and install the package to your local CQ instance as described above. Then cd to `content/src/main/content/jcr_root` and run

    vlt --credentials admin:admin checkout -f ../META-INF/vault/filter.xml --force http://localhost:4502/crx

Once the working copy is created, you can use the normal ``vlt up`` and ``vlt ci`` commands.

Specifying CRX Host/Port
------------------------

The CRX host and port can be specified on the command line with:
mvn -Dcrx.host=otherhost -Dcrx.port=5502 <goals>
