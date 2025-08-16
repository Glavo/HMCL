plugins {
    id("java-library")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

tasks.checkstyleMain {
    // Third-party code is not checked
    exclude("**")
}

tasks.compileJava {
    options.compilerArgs.addAll(
        listOf(
            "--add-exports=javafx.controls/com.sun.javafx.scene.control.behavior=com.jfoenix",
            "--add-exports=javafx.base/com.sun.javafx.binding=com.jfoenix",
            "--add-exports=javafx.base/com.sun.javafx.event=com.jfoenix",
            "--add-exports=javafx.graphics/com.sun.javafx.stage=com.jfoenix",
            "--add-exports=javafx.graphics/com.sun.javafx.scene=com.jfoenix",
            "--add-exports=javafx.graphics/com.sun.javafx.geom=com.jfoenix",
            "--add-exports=javafx.graphics/com.sun.javafx.scene.text=com.jfoenix",
            "--add-exports=javafx.controls/com.sun.javafx.scene.control.inputmap=com.jfoenix",
            "--add-exports=javafx.graphics/com.sun.javafx.scene.traversal=com.jfoenix",
            "--add-exports=javafx.controls/com.sun.javafx.scene.control=com.jfoenix",
            "--add-exports=javafx.graphics/com.sun.javafx.util=com.jfoenix",
        )
    )
}

dependencies {
    compileOnlyApi(libs.jetbrains.annotations)
}
