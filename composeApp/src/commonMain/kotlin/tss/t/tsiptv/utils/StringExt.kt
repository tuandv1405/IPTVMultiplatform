package tss.t.tsiptv.utils

fun String.isValidEmail() =
    this.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$"))

fun String.isValidPhoneNumber() = this.matches(Regex("^[0-9]{10}\$"))
fun String.isValidPassword() =
    this.matches(Regex("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$"))

fun String.isValidUrl() =
    this.matches(Regex("^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|](?:\\?[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*)?$"))
