package ZenodoAddon

import scala.reflect.ClassTag

object Utils {

  def getClassObjectFromString(className: String,
                               traitClass: Class[_]): Option[Class[_]] = {
    try {
      val classObject = Class.forName(className)

      if (traitClass.isAssignableFrom(classObject)) Some(classObject)
      else None
    } catch {
      case _:ClassNotFoundException => None
    }
  }

  def getInstanceObjectFromString[
    asInstance
  ](className: String)(implicit ct: ClassTag[asInstance]):
  Option[_ <: asInstance] = {
    getClassObjectFromString(
      className,
      ct.runtimeClass
    )
      .map(_.newInstance.asInstanceOf[asInstance])
  }

}
