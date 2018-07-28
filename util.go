package tipatch

import "github.com/hashicorp/errwrap"

// GetErrors returns the wrapped errors from one error.
func GetErrors(err error) []string {
	if err != nil {
		wrapped := err.(errwrap.Wrapper).WrappedErrors()
		return []string{wrapped[0].Error(), wrapped[1].Error()}
	}

	return []string{}
}
