package kiger.target

class Register(val name: String) {
    override fun toString() = name
    override fun equals(other: Any?) = other is Register && name == other.name
    override fun hashCode() = name.hashCode()
}
