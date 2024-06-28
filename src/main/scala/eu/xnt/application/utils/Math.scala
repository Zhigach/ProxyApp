package eu.xnt.application.utils

object Math {
    
    def roundBy(long: Long, to: Long): Long = {
        (long / to) * to
    }

}
