// validate-java-class-header.mjs의 정규식·fail-closed 판정을 검증한다.
// `node --test`로 실행되지 터미널에서 직접 실행하는 CLI가 아니라서 shebang은 안 붙인다
// (형제 파일 validate-java-class-header.mjs·validate-glossary.mjs와 다른 점).

import assert from 'node:assert/strict';
import test from 'node:test';

import { checkFile, inScope } from './validate-java-class-header.mjs';

test('accepts a type with both required header lines before its declaration', () => {
	const result = checkFile('Example.java', `
/**
 * Class Name : Example.java
 * Description : 테스트 타입이다.
 */
public class Example {}
`);

	assert.equal(result, null);
});

test('reports each missing header line', () => {
	const result = checkFile('Example.java', `
/**
 * Description : 테스트 타입이다.
 */
public record Example(String value) {}
`);

	assert.deepEqual(result, { path: 'Example.java', missing: ['Class Name :'] });
});

test('does not accept a header that appears after the type declaration', () => {
	const result = checkFile('Example.java', `
public class Example {}
/**
 * Class Name : Example.java
 * Description : 너무 늦은 헤더다.
 */
`);

	assert.deepEqual(result, { path: 'Example.java', missing: ['Class Name :', 'Description :'] });
});

test('does not treat a method-only Java file as a class header target', () => {
	const result = checkFile('package-info.java', '@Deprecated\npackage example;\n');

	assert.equal(result, null);
});

test('detects a declaration even when an annotation shares its line', () => {
	const result = checkFile('Example.java', `
import org.springframework.stereotype.Component;

@Component public class Example {}
`);

	assert.deepEqual(result, { path: 'Example.java', missing: ['Class Name :', 'Description :'] });
});

test('accepts a same-line annotated declaration when the header is present', () => {
	const result = checkFile('Example.java', `
/**
 * Class Name : Example.java
 * Description : 애노테이션과 선언이 한 줄이어도 통과해야 한다.
 */
@Component public class Example {}
`);

	assert.equal(result, null);
});

test('reports a Java file with no detectable type declaration as a violation, not a skip', () => {
	const result = checkFile('Weird.java', 'package example;\n// 타입 선언이 없는 이상한 파일\n');

	assert.deepEqual(result, { path: 'Weird.java', missing: ['타입 선언을 찾을 수 없음(검사기 한계일 수 있음)'] });
});

test('still exempts module-info.java from needing a type declaration', () => {
	const result = checkFile('module-info.java', 'module example {\n\trequires java.base;\n}\n');

	assert.equal(result, null);
});

test('limits the target to Java files', () => {
	assert.equal(inScope('backend/common/bff-web/src/main/java/Example.java'), true);
	assert.equal(inScope('scripts/validate-java-class-header.mjs'), false);
});
