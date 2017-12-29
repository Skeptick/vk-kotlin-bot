package tk.skeptick.bot

class HandlerOverrideException(description: String?) : IllegalArgumentException(description) {
    constructor() : this(null)
}