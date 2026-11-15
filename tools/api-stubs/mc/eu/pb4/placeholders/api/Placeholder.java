package eu.pb4.placeholders.api; public interface Placeholder<C, T> { @FunctionalInterface interface Handler<C, T> { PlaceholderResult onPlaceholderRequest(C context, T argument); } }
