import neotype.*

type AccountId = AccountId.Type
object AccountId extends Newtype[String]

type Amount = Amount.Type
object Amount extends Subtype[Int]

type Delay = Delay.Type
object Delay extends Newtype[Int]

type Die = Die.Type
object Die extends Newtype[Boolean]
