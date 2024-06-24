package eu.xnt.application.utils

object Math {
    
    extension (long: Long)
        def roundBy(to: Long): Long = {
            (long / to) * to
        }

}
