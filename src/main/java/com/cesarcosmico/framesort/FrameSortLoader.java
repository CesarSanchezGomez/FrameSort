package com.cesarcosmico.framesort;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.List;

/**
 * Downloads on first start the libraries the server does not ship. The server already has the SQLite and MySQL
 * drivers, and its class loader would always pick them over a downloaded copy.
 */
public final class FrameSortLoader implements PluginLoader {

    // Same HikariCP version as gradle.properties, which FrameSortLoaderTest checks.
    static final List<String> LIBRARIES = List.of(
            "com.zaxxer:HikariCP:7.1.0",
            "org.mariadb.jdbc:mariadb-java-client:3.5.10");
    // The server provides slf4j-api; a second copy would leave Hikari logging to nowhere.
    private static final List<Exclusion> WITHOUT_SLF4J = List.of(new Exclusion("org.slf4j", "slf4j-api", "*", "*"));

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        for (String coordinates : LIBRARIES) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null, false, WITHOUT_SLF4J));
        }
        classpathBuilder.addLibrary(resolver);
    }
}
