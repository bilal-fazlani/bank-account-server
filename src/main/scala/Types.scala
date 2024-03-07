import neotype.*

type AccountId = AccountId.Type
object AccountId extends Subtype[String]

type Amount = Amount.Type
object Amount extends Subtype[Int]

type TransactionId = TransactionId.Type
object TransactionId extends Subtype[String]

type Delay = Delay.Type
object Delay extends Subtype[Int]

type Die = Die.Type
object Die extends Subtype[Boolean]
