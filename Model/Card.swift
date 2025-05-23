import Foundation

// Define an enum Suit with cases: spades, hearts, diamonds, clubs.
enum Suit {
    case spades, hearts, diamonds, clubs
}

// Define an enum Rank with cases for Ace, 2 through 10, Jack, Queen, King.
// Make it CaseIterable to easily create a full deck.
// Consider giving ranks appropriate integer raw values (e.g., Ace = 1, King = 13)
// if that might be useful for game logic later, but simple cases are fine for now.
enum Rank: Int, CaseIterable {
    case ace = 1
    case two, three, four, five, six, seven, eight, nine, ten
    case jack, queen, king
}

// Define a struct Card with two properties:
// suit: Suit
// rank: Rank
// Make it Identifiable and Equatable for UI and comparison purposes.
struct Card: Identifiable, Equatable {
    let id = UUID() // Identifiable conformance
    let suit: Suit
    let rank: Rank

    // Equatable conformance (Swift can synthesize this for structs whose members are Equatable,
    // but it's good to be explicit or know how to do it if needed)
    // static func == (lhs: Card, rhs: Card) -> Bool {
    //     return lhs.suit == rhs.suit && lhs.rank == rhs.rank
    // }
}
