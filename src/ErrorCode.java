/**
 * The university of Melbourne
 * COMP90015: Distributed Systems
 * Project 1
 * Author: Yupeng Li
 * Student ID: 1399160
 * Date: 06/04/2023
 */

public enum ErrorCode {
	SUCCESS(0, "success"),
	NOT_FOUND(-1, "not found"),
	DUPLICATE(-2, "duplicate"),
	IO_ERROR(-3, "io error"),
	INVALID_PARAMETER(-4, "invalid parameter")
	;

	public int code() {
		return error_code;
	}

	public String context() {
		return error_context;
	}

	private final int error_code;
	private final String error_context;
	ErrorCode(int code, String context) {
		this.error_code = code;
		this.error_context = context;
	}
}
