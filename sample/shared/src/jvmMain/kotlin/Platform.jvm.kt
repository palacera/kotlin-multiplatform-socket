actual val platform: Platform by lazy {
    Platform(
        name = "Java ${System.getProperty("java.version")}",
    )
}
